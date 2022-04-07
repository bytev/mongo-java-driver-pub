/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bson.codecs.record;

import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.RepresentationConfigurable;
import org.bson.codecs.configuration.CodecConfigurationException;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.record.annotations.BsonProperty;
import org.bson.codecs.record.annotations.BsonId;
import org.bson.codecs.record.annotations.BsonRepresentation;
import org.bson.diagnostics.Logger;
import org.bson.diagnostics.Loggers;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.bson.assertions.Assertions.notNull;

final class RecordCodec<T extends Record> implements Codec<T> {
    private static final Logger LOGGER = Loggers.getLogger("RecordCodec");
    private final Class<T> clazz;
    private final Constructor<?> canonicalConstructor;
    private final List<ComponentModel> componentModels;
    private final ComponentModel componentModelForId;
    private final Map<String, ComponentModel> fieldNameToComponentModel;

    private static final class ComponentModel {
        private final RecordComponent component;
        private final Codec<?> codec;
        private final int index;
        private final String fieldName;

        private ComponentModel(final RecordComponent component, final CodecRegistry codecRegistry, final int index) {
            this.component = component;
            this.codec = computeCodec(component, codecRegistry);
            this.index = index;
            this.fieldName = computeFieldName(component);
        }

        String getComponentName() {
            return component.getName();
        }

        String getFieldName() {
            return fieldName;
        }

        Object getValue(final Record record) throws InvocationTargetException, IllegalAccessException {
            return component.getAccessor().invoke(record);
        }

        private static Codec<?> computeCodec(final RecordComponent component, final CodecRegistry codecRegistry) {
            var codec = codecRegistry.get(toWrapper(component.getType()));
            var bsonRepresentationAnnotation = component.getAnnotation(BsonRepresentation.class);
            if (bsonRepresentationAnnotation != null) {
                if (codec instanceof RepresentationConfigurable<?> representationConfigurable) {
                    codec = representationConfigurable.withRepresentation(bsonRepresentationAnnotation.value());
                } else {
                    throw new CodecConfigurationException(
                            format("Codec for %s must implement RepresentationConfigurable to support BsonRepresentation",
                                    codec.getEncoderClass()));
                }
            }
            return codec;
        }

        private static String computeFieldName(final RecordComponent component) {
            if (component.isAnnotationPresent(BsonId.class)) {
                return "_id";
            } else if (component.isAnnotationPresent(BsonProperty.class)) {
                return component.getAnnotation(BsonProperty.class).value();
            }
            return component.getName();
        }
    }

    RecordCodec(final Class<T> clazz, final CodecRegistry codecRegistry) {
        this.clazz = notNull("class", clazz);
        canonicalConstructor = notNull("canonicalConstructor", getCanonicalConstructor(clazz));
        componentModels = getComponentModels(clazz, codecRegistry);
        fieldNameToComponentModel = componentModels.stream()
                .collect(Collectors.toMap(ComponentModel::getFieldName, Function.identity()));
        componentModelForId = getComponentModelForId(clazz, componentModels);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T decode(final BsonReader reader, final DecoderContext decoderContext) {
        reader.readStartDocument();

        Object[] constructorArguments = new Object[componentModels.size()];
        while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            var fieldName = reader.readName();
            var componentModel = fieldNameToComponentModel.get(fieldName);
            if (componentModel == null) {
                reader.skipValue();
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(format("Found property not present in the ClassModel: %s", fieldName));
                }
            } else {
                constructorArguments[componentModel.index] = decoderContext.decodeWithChildContext(componentModel.codec, reader);
            }
        }
        reader.readEndDocument();

        try {
            return (T) canonicalConstructor.newInstance(constructorArguments);
        } catch (ReflectiveOperationException e) {
            throw new CodecConfigurationException(format("Unable to invoke canonical constructor of record class %s", clazz.getName()), e);
        }
    }

    @Override
    public void encode(final BsonWriter writer, final T record, final EncoderContext encoderContext) {
        writer.writeStartDocument();
        if (componentModelForId != null) {
            writeComponent(writer, record, componentModelForId);
        }
        for (var componentModel : componentModels) {
            if (componentModel == componentModelForId) {
                continue;
            }
            writeComponent(writer, record, componentModel);
        }
        writer.writeEndDocument();

    }

    @Override
    public Class<T> getEncoderClass() {
        return clazz;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeComponent(final BsonWriter writer, final T record, final ComponentModel componentModel) {
        try {
            Object componentValue = componentModel.getValue(record);
            if (componentValue != null) {
                writer.writeName(componentModel.getFieldName());
                ((Codec) componentModel.codec).encode(writer, componentValue, EncoderContext.builder().build());
            }
        } catch (ReflectiveOperationException e) {
            throw new CodecConfigurationException(
                    format("Unable to access value of component %s for record %s", componentModel.getComponentName(), clazz.getName()), e);
        }
    }

    private static <T> List<ComponentModel> getComponentModels(final Class<T> clazz, final CodecRegistry codecRegistry) {
        var recordComponents = clazz.getRecordComponents();
        var componentModels = new ArrayList<ComponentModel>(recordComponents.length);
        for (int i = 0; i < recordComponents.length; i++) {
            componentModels.add(new ComponentModel(recordComponents[i], codecRegistry, i));
        }
        return componentModels;
    }

    @Nullable
    private static <T> ComponentModel getComponentModelForId(final Class<T> clazz, final List<ComponentModel> componentModels) {
        List<ComponentModel> componentModelsForId = componentModels.stream()
                .filter(componentModel -> componentModel.getFieldName().equals("_id")).toList();
        if (componentModelsForId.size() > 1) {
            throw new CodecConfigurationException(format("Record %s has more than one _id component", clazz.getName()));
        } else {
            return componentModelsForId.stream().findFirst().orElse(null);
        }
    }

    private static <T> Constructor<?> getCanonicalConstructor(final Class<T> clazz) {
        Class<?>[] recordComponentTypes = Arrays.stream(clazz.getRecordComponents()).map(RecordComponent::getType).toArray(Class<?>[]::new);
        for (var constructor : clazz.getConstructors()) {
            if (Arrays.equals(constructor.getParameterTypes(), recordComponentTypes)) {
                return constructor;
            }
        }
        throw new AssertionError(format("Could not find canonical constructor for record %s", clazz.getName()));
    }

    private static Class<?> toWrapper(final Class<?> clazz) {
        if (clazz == Integer.TYPE) {
            return Integer.class;
        } else if (clazz == Long.TYPE) {
            return Long.class;
        } else if (clazz == Boolean.TYPE) {
            return Boolean.class;
        } else if (clazz == Byte.TYPE) {
            return Byte.class;
        } else if (clazz == Character.TYPE) {
            return Character.class;
        } else if (clazz == Float.TYPE) {
            return Float.class;
        } else if (clazz == Double.TYPE) {
            return Double.class;
        } else if (clazz == Short.TYPE) {
            return Short.class;
        } else {
            return clazz;
        }
    }
}
