/*
 * Copyright 2017 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bson.codecs.pojo;

import org.bson.codecs.configuration.CodecConfigurationException;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.entities.AbstractInterfaceModel;
import org.bson.codecs.pojo.entities.AsymmetricalCreatorModel;
import org.bson.codecs.pojo.entities.AsymmetricalIgnoreModel;
import org.bson.codecs.pojo.entities.AsymmetricalModel;
import org.bson.codecs.pojo.entities.CollectionNestedPojoModel;
import org.bson.codecs.pojo.entities.ConcreteAndNestedAbstractInterfaceModel;
import org.bson.codecs.pojo.entities.ConcreteCollectionsModel;
import org.bson.codecs.pojo.entities.ConcreteStandAloneAbstractInterfaceModel;
import org.bson.codecs.pojo.entities.ConstructorNotPublicModel;
import org.bson.codecs.pojo.entities.ConventionModel;
import org.bson.codecs.pojo.entities.ConverterModel;
import org.bson.codecs.pojo.entities.GenericHolderModel;
import org.bson.codecs.pojo.entities.GenericTreeModel;
import org.bson.codecs.pojo.entities.InterfaceBasedModel;
import org.bson.codecs.pojo.entities.InterfaceModelImpl;
import org.bson.codecs.pojo.entities.InvalidGetterAndSetterModel;
import org.bson.codecs.pojo.entities.InvalidSetterArgsModel;
import org.bson.codecs.pojo.entities.MultipleBoundsModel;
import org.bson.codecs.pojo.entities.MultipleLevelGenericModel;
import org.bson.codecs.pojo.entities.NestedFieldReusingClassTypeParameter;
import org.bson.codecs.pojo.entities.NestedGenericHolderFieldWithMultipleTypeParamsModel;
import org.bson.codecs.pojo.entities.NestedGenericHolderMapModel;
import org.bson.codecs.pojo.entities.NestedGenericHolderModel;
import org.bson.codecs.pojo.entities.NestedGenericHolderSimpleGenericsModel;
import org.bson.codecs.pojo.entities.NestedGenericTreeModel;
import org.bson.codecs.pojo.entities.NestedMultipleLevelGenericModel;
import org.bson.codecs.pojo.entities.NestedReusedGenericsModel;
import org.bson.codecs.pojo.entities.NestedSelfReferentialGenericHolderModel;
import org.bson.codecs.pojo.entities.NestedSelfReferentialGenericModel;
import org.bson.codecs.pojo.entities.PrimitivesModel;
import org.bson.codecs.pojo.entities.PropertyReusingClassTypeParameter;
import org.bson.codecs.pojo.entities.PropertySelectionModel;
import org.bson.codecs.pojo.entities.PropertyWithMultipleTypeParamsModel;
import org.bson.codecs.pojo.entities.ReusedGenericsModel;
import org.bson.codecs.pojo.entities.SelfReferentialGenericModel;
import org.bson.codecs.pojo.entities.ShapeHolderModel;
import org.bson.codecs.pojo.entities.ShapeModelAbstract;
import org.bson.codecs.pojo.entities.ShapeModelCircle;
import org.bson.codecs.pojo.entities.ShapeModelRectangle;
import org.bson.codecs.pojo.entities.SimpleEnum;
import org.bson.codecs.pojo.entities.SimpleEnumModel;
import org.bson.codecs.pojo.entities.SimpleGenericsModel;
import org.bson.codecs.pojo.entities.SimpleModel;
import org.bson.codecs.pojo.entities.SimpleNestedPojoModel;
import org.bson.codecs.pojo.entities.UpperBoundsConcreteModel;
import org.bson.codecs.pojo.entities.conventions.CreatorAllFinalFieldsModel;
import org.bson.codecs.pojo.entities.conventions.CreatorConstructorModel;
import org.bson.codecs.pojo.entities.conventions.CreatorConstructorThrowsExceptionModel;
import org.bson.codecs.pojo.entities.conventions.CreatorMethodModel;
import org.bson.codecs.pojo.entities.conventions.CreatorMethodThrowsExceptionModel;
import org.bson.codecs.pojo.entities.conventions.CreatorNoArgsConstructorModel;
import org.bson.codecs.pojo.entities.conventions.CreatorNoArgsMethodModel;
import org.bson.types.ObjectId;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.bson.codecs.pojo.Conventions.NO_CONVENTIONS;

public final class PojoCodecTest extends PojoTestCase {

    @Test
    public void testRoundTripSimpleModel() {
        SimpleModel model = getSimpleModel();
        roundTrip(getPojoCodecProviderBuilder(SimpleModel.class), model, SIMPLE_MODEL_JSON);
    }

    @Test
    public void testFieldModifiersModel() {
        PropertySelectionModel model = new PropertySelectionModel();
        roundTrip(getPojoCodecProviderBuilder(PropertySelectionModel.class), model,
                "{'finalStringField': 'finalStringField', 'stringField': 'stringField'}");
    }

    @Test
    public void testRoundTripPrimitivesModel() {
        PrimitivesModel model = getPrimitivesModel();
        roundTrip(getPojoCodecProviderBuilder(PrimitivesModel.class), model,
                "{ 'myBoolean': true, 'myByte': 1, 'myCharacter': '1', 'myDouble': 1.0, 'myFloat': 2.0, 'myInteger': 3, "
                        + "'myLong': { '$numberLong': '5' }, 'myShort': 6}");
    }

    @Test
    public void testNumberCoercionInPrimitivesModel() {
        PrimitivesModel model = getPrimitivesModel();
        decodesTo(getCodecRegistry(getPojoCodecProviderBuilder(PrimitivesModel.class)),
                "{ 'myBoolean': true, 'myByte': 1.0, 'myCharacter': '1', 'myDouble': 1, 'myFloat': 2, "
                        + "'myInteger': { '$numberLong': '3' }, 'myLong': 5.0, 'myShort': { '$numberLong': '6' }}", model);
    }

    @Test
    public void testRoundTripConcreteCollectionsModel() {
        ConcreteCollectionsModel  model = getConcreteCollectionsModel();
        roundTrip(getPojoCodecProviderBuilder(ConcreteCollectionsModel.class), model,
                "{'collection': [1, 2, 3], 'list': [4, 5, 6], 'linked': [7, 8, 9], 'map': {'A': 1.1, 'B': 2.2, 'C': 3.3},"
                        + "'concurrent': {'D': 4.4, 'E': 5.5, 'F': 6.6}}");
    }

    @Test
    public void testRoundTripSimpleNestedPojoModel() {
        SimpleNestedPojoModel model = getSimpleNestedPojoModel();
        roundTrip(getPojoCodecProviderBuilder(SimpleNestedPojoModel.class, SimpleModel.class), model,
                "{'simple': " + SIMPLE_MODEL_JSON + "}");
    }

    @Test
    public void testRoundTripCollectionNestedPojoModel() {
        CollectionNestedPojoModel model = getCollectionNestedPojoModel();
        roundTrip(getPojoCodecProviderBuilder(CollectionNestedPojoModel.class, SimpleModel.class), model,
                "{ 'listSimple': [" + SIMPLE_MODEL_JSON + "],"
                        + "'listListSimple': [[" + SIMPLE_MODEL_JSON + "]],"
                        + "'setSimple': [" + SIMPLE_MODEL_JSON + "],"
                        + "'setSetSimple': [[" + SIMPLE_MODEL_JSON + "]],"
                        + "'mapSimple': {'s': " + SIMPLE_MODEL_JSON + "},"
                        + "'mapMapSimple': {'ms': {'s': " + SIMPLE_MODEL_JSON + "}},"
                        + "'mapListSimple': {'ls': [" + SIMPLE_MODEL_JSON + "]},"
                        + "'mapListMapSimple': {'lm': [{'s': " + SIMPLE_MODEL_JSON + "}]},"
                        + "'mapSetSimple': {'s': [" + SIMPLE_MODEL_JSON + "]},"
                        + "'listMapSimple': [{'s': " + SIMPLE_MODEL_JSON + "}],"
                        + "'listMapListSimple': [{'ls': [" + SIMPLE_MODEL_JSON + "]}],"
                        + "'listMapSetSimple': [{'s': [" + SIMPLE_MODEL_JSON + "]}],"
                        + "}");
    }

    @Test
    public void testShapeModelAbstract() {
        PojoCodecProvider.Builder builder = getPojoCodecProviderBuilder(ShapeModelAbstract.class,
                ShapeModelCircle.class, ShapeModelRectangle.class, ShapeHolderModel.class);

        roundTrip(builder, new ShapeHolderModel(getShapeModelCircle()),
                "{'shape': {'_t': 'ShapeModelCircle', 'color': 'orange', 'radius': 4.2}}");

        roundTrip(builder, new ShapeHolderModel(getShapeModelRectangle()),
                "{'shape': {'_t': 'ShapeModelRectangle', 'color': 'green', 'width': 22.1, 'height': 105.0} }");
    }

    @Test
    public void testInheritedDiscriminatorAnnotation() {
        PojoCodecProvider.Builder builder = getPojoCodecProviderBuilder(ShapeModelCircle.class, ShapeModelRectangle.class);

        roundTrip(builder, getShapeModelCircle(),
                "{'_t': 'ShapeModelCircle', 'color': 'orange', 'radius': 4.2}");

        roundTrip(builder, getShapeModelRectangle(),
                "{'_t': 'ShapeModelRectangle', 'color': 'green', 'width': 22.1, 'height': 105.0}");
    }

    @Test
    public void testUpperBoundsConcreteModel() {
        PojoCodecProvider.Builder builder = getPojoCodecProviderBuilder(UpperBoundsConcreteModel.class);
        roundTrip(builder, new UpperBoundsConcreteModel(1L),
                "{'myGenericField': {'$numberLong': '1'}}");
    }

    @Test
    public void testNestedGenericHolderModel() {
        PojoCodecProvider.Builder builder = getPojoCodecProviderBuilder(NestedGenericHolderModel.class, GenericHolderModel.class);
        roundTrip(builder, getNestedGenericHolderModel(),
                "{'nested': {'myGenericField': 'generic', 'myLongField': {'$numberLong': '1'}}}");
    }

    @Test
    public void testNestedGenericHolderMapModel() {
        PojoCodecProvider.Builder builder = getPojoCodecProviderBuilder(NestedGenericHolderMapModel.class,
                GenericHolderModel.class, SimpleGenericsModel.class, SimpleModel.class);
        roundTrip(builder, getNestedGenericHolderMapModel(),
                "{ 'nested': { 'myGenericField': {'s': " + SIMPLE_MODEL_JSON + "}, 'myLongField': {'$numberLong': '1'}}}");
    }

    @Test
    public void testNestedReusedGenericsModel() {
        PojoCodecProvider.Builder builder = getPojoCodecProviderBuilder(NestedReusedGenericsModel.class, ReusedGenericsModel.class,
                SimpleModel.class);
        roundTrip(builder, getNestedReusedGenericsModel(),
                "{ 'nested':{ 'field1':{ '$numberLong':'1' }, 'field2':[" + SIMPLE_MODEL_JSON + "], "
                        + "'field3':'field3', 'field4':42, 'field5':'field5', 'field6':[" + SIMPLE_MODEL_JSON + ", "
                        + SIMPLE_MODEL_JSON + "], 'field7':{ '$numberLong':'2' }, 'field8':'field8' } }");
    }

    @Test
    public void testMultipleBoundsModel() {
        PojoCodecProvider.Builder builder = getPojoCodecProviderBuilder(MultipleBoundsModel.class);
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("key", "value");
        List<Integer> list = asList(1, 2, 3);
        roundTrip(builder, new MultipleBoundsModel(map, list, 2.2),
                "{'level1' : 2.2, 'level2': [1, 2, 3], 'level3': {key: 'value'}}");
    }

    @Test
    public void testNestedGenericHolderFieldWithMultipleTypeParamsModel() {
        PojoCodecProvider.Builder builder = getPojoCodecProviderBuilder(NestedGenericHolderFieldWithMultipleTypeParamsModel.class,
                PropertyWithMultipleTypeParamsModel.class, SimpleGenericsModel.class, GenericHolderModel.class).conventions(NO_CONVENTIONS);

        SimpleGenericsModel<Long, String, Integer> simple = getSimpleGenericsModelAlt();
        PropertyWithMultipleTypeParamsModel<Integer, Long, String> field =
                new PropertyWithMultipleTypeParamsModel<Integer, Long, String>(simple);
        GenericHolderModel<PropertyWithMultipleTypeParamsModel<Integer, Long, String>> nested = new
                GenericHolderModel<PropertyWithMultipleTypeParamsModel<Integer, Long, String>>(field, 42L);
        roundTrip(builder, new NestedGenericHolderFieldWithMultipleTypeParamsModel(nested),
                "{'nested': {'myGenericField': "
                        + "{'simpleGenericsModel': {'myIntegerField': 42, 'myGenericField': {'$numberLong': '101'}, "
                        + " 'myListField': ['B', 'C'], 'myMapField': {'D': 2, 'E': 3, 'F': 4 }}}, 'myLongField': {'$numberLong': '42'}}}");
    }

    @Test
    public void testNestedGenericTreeModel(){
        PojoCodecProvider.Builder builder = getPojoCodecProviderBuilder(NestedGenericTreeModel.class, GenericTreeModel.class);
        roundTrip(builder, new NestedGenericTreeModel(42, getGenericTreeModel()),
                "{'intField': 42, 'nested': {'field1': 'top', 'field2': 1, "
                        + "'left': {'field1': 'left', 'field2': 2, 'left': {'field1': 'left', 'field2': 3}}, "
                        + "'right': {'field1': 'right', 'field2': 4, 'left': {'field1': 'left', 'field2': 5}}}}");
    }

    @Test
    public void testNestedMultipleLevelGenericModel() {
        PojoCodecProvider.Builder builder = getPojoCodecProviderBuilder(NestedMultipleLevelGenericModel.class,
                MultipleLevelGenericModel.class, GenericTreeModel.class);

        String json = "{'intField': 42, 'nested': {'stringField': 'string', 'nested': {'field1': 'top', 'field2': 1, "
                + "'left': {'field1': 'left', 'field2': 2, 'left': {'field1': 'left', 'field2': 3}}, "
                + "'right': {'field1': 'right', 'field2': 4, 'left': {'field1': 'left', 'field2': 5}}}}}";

        roundTrip(builder,
                new NestedMultipleLevelGenericModel(42, new MultipleLevelGenericModel<String>("string", getGenericTreeModel())),
                json);
    }

    @Test
    public void testGenericsRoundTrip() {
        // Multiple levels of nesting
        SimpleModel simpleModel = getSimpleModel();
        Map<String, SimpleModel> map = new HashMap<String, SimpleModel>();
        map.put("A", simpleModel);
        Map<String, Map<String, SimpleModel>> mapB = new HashMap<String, Map<String, SimpleModel>>();
        mapB.put("A", map);
        SimpleGenericsModel<Integer, List<SimpleModel>, Map<String, SimpleModel>> simpleGenericsModel =
                new SimpleGenericsModel<Integer, List<SimpleModel>, Map<String, SimpleModel>>(42, 42,
                        singletonList(singletonList(simpleModel)), mapB);
        GenericHolderModel<SimpleGenericsModel<Integer, List<SimpleModel>, Map<String, SimpleModel>>> nested =
                new GenericHolderModel<SimpleGenericsModel<Integer, List<SimpleModel>, Map<String, SimpleModel>>>(simpleGenericsModel, 42L);

        NestedGenericHolderSimpleGenericsModel model = new NestedGenericHolderSimpleGenericsModel(nested);

        PojoCodecProvider.Builder builder = getPojoCodecProviderBuilder(NestedGenericHolderSimpleGenericsModel.class,
                GenericHolderModel.class, SimpleGenericsModel.class, SimpleModel.class);
        roundTrip(builder, model,
                "{'nested': {'myGenericField': {'myIntegerField': 42, 'myGenericField': 42,"
                        + "                           'myListField': [[" + SIMPLE_MODEL_JSON + "]], "
                        + "                           'myMapField': {'A': {'A': " + SIMPLE_MODEL_JSON + "}}},"
                        + "         'myLongField': {'$numberLong': '42' }}}");
    }

    @Test
    public void testNestedFieldReusingClassTypeParameter() {
        NestedFieldReusingClassTypeParameter model = new NestedFieldReusingClassTypeParameter(
                new PropertyReusingClassTypeParameter<String>(getGenericTreeModelStrings()));
        roundTrip(getPojoCodecProviderBuilder(NestedFieldReusingClassTypeParameter.class, PropertyReusingClassTypeParameter.class,
                GenericTreeModel.class), model,
                "{'nested': {'tree': {'field1': 'top', 'field2': '1', "
                        + "'left': {'field1': 'left', 'field2': '2', 'left': {'field1': 'left', 'field2': '3'}}, "
                        + "'right': {'field1': 'right', 'field2': '4', 'left': {'field1': 'left', 'field2': '5'}}}}}");
    }

    @Test
    public void testSelfReferentialGenerics() {
        SelfReferentialGenericModel<Boolean, Long> selfRef1 = new SelfReferentialGenericModel<Boolean, Long>(true, 33L,
                new SelfReferentialGenericModel<Long, Boolean>(44L, false, null));
        SelfReferentialGenericModel<Boolean, Double> selfRef2 = new SelfReferentialGenericModel<Boolean, Double>(true, 3.14,
                new SelfReferentialGenericModel<Double, Boolean>(3.42, true, null));
        NestedSelfReferentialGenericModel<Boolean, Long, Double> nested =
                new NestedSelfReferentialGenericModel<Boolean, Long, Double>(true, 42L, 44.0, selfRef1, selfRef2);
        NestedSelfReferentialGenericHolderModel model = new NestedSelfReferentialGenericHolderModel(nested);

        roundTrip(getPojoCodecProviderBuilder(NestedSelfReferentialGenericHolderModel.class, NestedSelfReferentialGenericModel.class,
                SelfReferentialGenericModel.class), model,
                "{'nested': { 't': true, 'v': {'$numberLong': '42'}, 'z': 44.0, "
                        + "'selfRef1': {'t': true, 'v': {'$numberLong': '33'}, 'child': {'t': {'$numberLong': '44'}, 'v': false}}, "
                        + "'selfRef2': {'t': true, 'v': 3.14, 'child': {'t': 3.42, 'v': true}}}}");
    }

    @Test
    public void testInterfaceBasedModel() {
        InterfaceBasedModel model = new ConcreteAndNestedAbstractInterfaceModel("A",
                new ConcreteAndNestedAbstractInterfaceModel("B", new ConcreteStandAloneAbstractInterfaceModel("C")));
        roundTrip(getPojoCodecProviderBuilder(InterfaceBasedModel.class, AbstractInterfaceModel.class,
                ConcreteAndNestedAbstractInterfaceModel.class, ConcreteStandAloneAbstractInterfaceModel.class), model,
                "{'_t': 'ConcreteAndNestedAbstractInterfaceModel', 'name': 'A', "
                        + "'child': {'_t': 'ConcreteAndNestedAbstractInterfaceModel', 'name': 'B', "
                        + "  'child': {'_t': 'ConcreteStandAloneAbstractInterfaceModel', 'name': 'C'}}}}");
    }

    @Test
    public void testInterfaceModelImpl() {
        InterfaceModelImpl model = new InterfaceModelImpl("a", "b");
        roundTrip(getPojoCodecProviderBuilder(InterfaceModelImpl.class), model, "{'propertyA': 'a', 'propertyB': 'b'}");
    }

    @Test
    public void testConventionsDefault() {
        ConventionModel model = getConventionModel();
        roundTrip(getPojoCodecProviderBuilder(ConventionModel.class, SimpleModel.class), model,
                "{'_id': 'id', '_cls': 'AnnotatedConventionModel', 'myFinalField': 10, 'myIntField': 10, "
                        + "'child': {'_id': 'child', 'myFinalField': 10, 'myIntField': 10,"
                        + "'model': {'integerField': 42, 'stringField': 'myString'}}}");
    }

    @Test
    public void testAsymmetricalModel() {
        AsymmetricalModel model = new AsymmetricalModel(42);
        CodecRegistry registry = getCodecRegistry(getPojoCodecProviderBuilder(AsymmetricalModel.class));

        encodesTo(registry, model, "{foo: 42}");
        decodesTo(registry, "{bar: 42}", model);
    }

    @Test
    public void testAsymmetricalCreatorModel() {
        AsymmetricalCreatorModel model = new AsymmetricalCreatorModel("Foo", "Bar");
        CodecRegistry registry = getCodecRegistry(getPojoCodecProviderBuilder(AsymmetricalCreatorModel.class));

        encodesTo(registry, model, "{baz: 'FooBar'}");
        decodesTo(registry, "{a: 'Foo', b: 'Bar'}", model);
    }

    @Test
    public void testAsymmetricalIgnoreModel() {
        AsymmetricalIgnoreModel encode = new AsymmetricalIgnoreModel("property", "getter", "setter", "getterAndSetter");
        AsymmetricalIgnoreModel decoded = new AsymmetricalIgnoreModel();
        decoded.setGetterIgnored("getter");

        CodecRegistry registry = getCodecRegistry(getPojoCodecProviderBuilder(AsymmetricalIgnoreModel.class));

        encodesTo(registry, encode, "{'setterIgnored': 'setter'}");
        decodesTo(registry, "{'propertyIgnored': 'property', 'getterIgnored': 'getter', 'setterIgnored': 'setter', "
                + "'getterAndSetterIgnored': 'getterAndSetter'}", decoded);
    }

    @Test
    public void testConventionsEmpty() {
        ConventionModel model = getConventionModel();
        ClassModelBuilder<ConventionModel> classModel = ClassModel.builder(ConventionModel.class)
                .conventions(NO_CONVENTIONS);
        ClassModelBuilder<SimpleModel> nestedClassModel = ClassModel.builder(SimpleModel.class).conventions(NO_CONVENTIONS);

        roundTrip(getPojoCodecProviderBuilder(classModel, nestedClassModel), model,
                "{'myFinalField': 10, 'myIntField': 10, 'customId': 'id',"
                        + "'child': {'myFinalField': 10, 'myIntField': 10, 'customId': 'child',"
                        + "          'simpleModel': {'integerField': 42, 'stringField': 'myString' } } }");
    }

    @Test
    public void testConventionsCustom() {
        ConventionModel model = getConventionModel();
        List<Convention> conventions = Collections.<Convention>singletonList(
                new Convention() {
                    @Override
                    public void apply(final ClassModelBuilder<?> classModelBuilder) {
                        for (PropertyModelBuilder<?> fieldModelBuilder : classModelBuilder.getPropertyModelBuilders()) {
                            fieldModelBuilder.discriminatorEnabled(false);
                            fieldModelBuilder.readName(
                                    fieldModelBuilder.getName()
                                            .replaceAll("([^_A-Z])([A-Z])", "$1_$2").toLowerCase());
                            fieldModelBuilder.writeName(
                                    fieldModelBuilder.getName()
                                            .replaceAll("([^_A-Z])([A-Z])", "$1_$2").toLowerCase());
                        }
                        if (classModelBuilder.getProperty("customId") != null) {
                            classModelBuilder.idPropertyName("customId");
                        }
                        classModelBuilder.enableDiscriminator(true);
                        classModelBuilder.discriminatorKey("_cls");
                        classModelBuilder.discriminator(classModelBuilder.getType().getSimpleName()
                                .replaceAll("([^_A-Z])([A-Z])", "$1_$2").toLowerCase());
                    }
                });

        ClassModelBuilder<ConventionModel> classModel = ClassModel.builder(ConventionModel.class).conventions(conventions);
        ClassModelBuilder<SimpleModel> nestedClassModel = ClassModel.builder(SimpleModel.class).conventions(conventions);

        roundTrip(getPojoCodecProviderBuilder(classModel, nestedClassModel), model,
                "{ '_id': 'id', '_cls': 'convention_model', 'my_final_field': 10, 'my_int_field': 10,"
                        + "'child': { '_id': 'child', 'my_final_field': 10, 'my_int_field': 10, "
                        + "           'simple_model': {'integer_field': 42, 'string_field': 'myString' } } }");
    }

    @Test
    public void testEnumSupport() {
        SimpleEnumModel model = new SimpleEnumModel(SimpleEnum.BRAVO);
        roundTrip(getPojoCodecProviderBuilder(SimpleEnumModel.class), model, "{ 'myEnum': 'BRAVO' }");
    }

    @Test
    public void testEnumSupportWithCustomCodec() {
        SimpleEnumModel model = new SimpleEnumModel(SimpleEnum.BRAVO);
        CodecRegistry registry = fromRegistries(getCodecRegistry(getPojoCodecProviderBuilder(SimpleEnumModel.class)),
                fromCodecs(new SimpleEnumCodec()));
        roundTrip(registry, model, "{ 'myEnum': 1 }");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCustomCodec() {
        ObjectId id = new ObjectId();
        ConverterModel model = new ConverterModel(id.toHexString(), "myName");

        ClassModelBuilder<ConverterModel> classModel = ClassModel.builder(ConverterModel.class);
        PropertyModelBuilder<String> idPropertyModelBuilder = (PropertyModelBuilder<String>) classModel.getProperty("id");
        idPropertyModelBuilder.codec(new StringToObjectIdCodec());

        roundTrip(getPojoCodecProviderBuilder(classModel), model,
                format("{'_id': {'$oid': '%s'}, 'name': 'myName'}", id.toHexString()));
    }

    @Test
    public void testCreatorConstructorModel() {
        CreatorConstructorModel model = new CreatorConstructorModel(10, "eleven", 12);
        roundTrip(getPojoCodecProviderBuilder(CreatorConstructorModel.class), model,
                "{'integerField': 10, 'stringField': 'eleven', 'longField': {$numberLong: '12'}}");
    }

    @Test
    public void testCreatorNoArgsConstructorModel() {
        CreatorNoArgsConstructorModel model = new CreatorNoArgsConstructorModel(10, "eleven", 12);
        roundTrip(getPojoCodecProviderBuilder(CreatorNoArgsConstructorModel.class), model,
                "{'integerField': 10, 'stringField': 'eleven', 'longField': {$numberLong: '12'}}");
    }

    @Test
    public void testCreatorMethodModel() {
        CreatorMethodModel model = new CreatorMethodModel(10, "eleven", 12);
        roundTrip(getPojoCodecProviderBuilder(CreatorMethodModel.class), model,
                "{'stringField': 'eleven', 'longField': {$numberLong: '12'}, 'integerField': 10}");
    }

    @Test
    public void testCreatorNoArgsMethodModel() {
        CreatorNoArgsMethodModel model = new CreatorNoArgsMethodModel(10, "eleven", 12);
        roundTrip(getPojoCodecProviderBuilder(CreatorNoArgsMethodModel.class), model,
                "{'stringField': 'eleven', 'integerField': 10, 'longField': {$numberLong: '12'}}");
    }

    @Test
    public void testCreatorAllFinalFieldsModel() {
        CreatorAllFinalFieldsModel model = new CreatorAllFinalFieldsModel("pId", "Ada", "Lovelace");
        roundTrip(getPojoCodecProviderBuilder(CreatorAllFinalFieldsModel.class), model,
                "{'_id': 'pId', '_t': 'CreatorAllFinalFieldsModel', 'firstName': 'Ada', 'lastName': 'Lovelace'}");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCustomFieldSerializer() {
        SimpleModel model = getSimpleModel();
        model.setIntegerField(null);
        ClassModelBuilder<SimpleModel> classModel = ClassModel.builder(SimpleModel.class);
        ((PropertyModelBuilder<Integer>) classModel.getProperty("integerField"))
                .propertySerialization(new PropertySerialization<Integer>() {
                    @Override
                    public boolean shouldSerialize(final Integer value) {
                        return true;
                    }
                });

        roundTrip(getPojoCodecProviderBuilder(classModel), model, "{'integerField': null, 'stringField': 'myString'}");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCanHandleNullValuesForNestedModels() {
        SimpleNestedPojoModel model = getSimpleNestedPojoModel();
        model.setSimple(null);
        ClassModelBuilder<SimpleNestedPojoModel> classModel = ClassModel.builder(SimpleNestedPojoModel.class);
        ((PropertyModelBuilder<SimpleModel>) classModel.getProperty("simple"))
                .propertySerialization(new PropertySerialization<SimpleModel>() {
                    @Override
                    public boolean shouldSerialize(final SimpleModel value) {
                        return true;
                    }
                });
        ClassModelBuilder<SimpleModel> classModelSimple = ClassModel.builder(SimpleModel.class);

        roundTrip(getPojoCodecProviderBuilder(classModel, classModelSimple), model, "{'simple': null}");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCanHandleNullValuesForCollectionsAndMaps() {
        ConcreteCollectionsModel model = getConcreteCollectionsModel();
        model.setCollection(null);
        model.setMap(null);

        ClassModelBuilder<ConcreteCollectionsModel> classModel =
                ClassModel.builder(ConcreteCollectionsModel.class);
        ((PropertyModelBuilder<Collection<Integer>>) classModel.getProperty("collection"))
                .propertySerialization(new PropertySerialization<Collection<Integer>>() {
                    @Override
                    public boolean shouldSerialize(final Collection<Integer> value) {
                        return true;
                    }
                });
        ((PropertyModelBuilder<Map<String, Double>>) classModel.getProperty("map"))
                .propertySerialization(new PropertySerialization<Map<String, Double>>() {
                    @Override
                    public boolean shouldSerialize(final Map<String, Double> value) {
                        return true;
                    }
                });

        roundTrip(getPojoCodecProviderBuilder(classModel), model,
                "{'collection': null, 'list': [4, 5, 6], 'linked': [7, 8, 9], 'map': null,"
                        + "'concurrent': {'D': 4.4, 'E': 5.5, 'F': 6.6}}");
    }

    @Test
    public void testCanHandleExtraData() {
        decodesTo(getCodec(SimpleModel.class), "{'integerField': 42,  'stringField': 'myString', 'extraFieldA': 1, 'extraFieldB': 2}",
                getSimpleModel());
    }

    @Test
    public void testDataCanHandleMissingData() {
        SimpleModel model = getSimpleModel();
        model.setIntegerField(null);

        decodesTo(getCodec(SimpleModel.class), "{'_t': 'SimpleModel', 'stringField': 'myString'}", model);
    }

    @Test(expected = CodecConfigurationException.class)
    public void testConstructorNotPublicModel() {
        decodingShouldFail(getCodec(ConstructorNotPublicModel.class), "{'integerField': 99}");
    }

    @Test(expected = CodecConfigurationException.class)
    public void testDataUnknownClass() {
        ClassModel<SimpleModel> classModel = ClassModel.builder(SimpleModel.class).enableDiscriminator(true).build();
        decodingShouldFail(getCodec(PojoCodecProvider.builder().register(classModel), SimpleModel.class), "{'_t': 'FakeModel'}");
    }

    @Test(expected = CodecConfigurationException.class)
    public void testInvalidTypeForField() {
        decodingShouldFail(getCodec(SimpleModel.class), "{'_t': 'SimpleModel', 'stringField': 123}");
    }

    @Test(expected = CodecConfigurationException.class)
    public void testInvalidTypeForPrimitiveField() {
        decodingShouldFail(getCodec(PrimitivesModel.class), "{ '_t': 'PrimitivesModel', 'myBoolean': null}");
    }

    @Test(expected = CodecConfigurationException.class)
    public void testInvalidTypeForModelField() {
        decodingShouldFail(getCodec(SimpleNestedPojoModel.class), "{ '_t': 'SimpleNestedPojoModel', 'simple': 123}");
    }

    @Test(expected = CodecConfigurationException.class)
    public void testInvalidDiscriminatorInNestedModel() {
        decodingShouldFail(getCodec(SimpleNestedPojoModel.class), "{ '_t': 'SimpleNestedPojoModel',"
                + "'simple': {'_t': 'FakeModel', 'integerField': 42, 'stringField': 'myString'}}");
    }

    @Test(expected = CodecConfigurationException.class)
    public void testCannotEncodeUnspecializedClasses() {
        CodecRegistry registry = fromProviders(getPojoCodecProviderBuilder(GenericTreeModel.class).build());
        encode(registry.get(GenericTreeModel.class), getGenericTreeModel());
    }

    @Test(expected = CodecConfigurationException.class)
    public void testCannotDecodeUnspecializedClasses() {
        decodingShouldFail(getCodec(GenericTreeModel.class),
                "{'field1': 'top', 'field2': 1, "
                        + "'left': {'field1': 'left', 'field2': 2, 'left': {'field1': 'left', 'field2': 3}}, "
                        + "'right': {'field1': 'right', 'field2': 4, 'left': {'field1': 'left', 'field2': 5}}}");
    }

    @Test(expected = CodecConfigurationException.class)
    public void testCreatorMethodModelWithMissingParameters() {
        decodingShouldFail(getCodec(CreatorMethodModel.class), "{'stringField': 'eleven', 'longField': {$numberLong: '12'}}");
    }

    @Test(expected = CodecConfigurationException.class)
    public void testCreatorMethodThrowsExceptionModel() {
        decodingShouldFail(getCodec(CreatorMethodThrowsExceptionModel.class),
                "{'integerField': 10, 'stringField': 'eleven', 'longField': {$numberLong: '12'}}");
    }

    @Test(expected = CodecConfigurationException.class)
    public void testCreatorConstructorThrowsExceptionModel() {
        decodingShouldFail(getCodec(CreatorConstructorThrowsExceptionModel.class), "{}");
    }

    @Test(expected = CodecConfigurationException.class)
    public void testInvalidSetterModel() {
        decodingShouldFail(getCodec(InvalidSetterArgsModel.class), "{'integerField': 42, 'stringField': 'myString'}");
    }

    @Test(expected = CodecConfigurationException.class)
    public void testInvalidGetterAndSetterModelEncoding() {
        InvalidGetterAndSetterModel model = new InvalidGetterAndSetterModel(42, "myString");
        roundTrip(getPojoCodecProviderBuilder(InvalidGetterAndSetterModel.class), model, "{'integerField': 42, 'stringField': 'myString'}");
    }

    @Test(expected = CodecConfigurationException.class)
    public void testInvalidGetterAndSetterModelDecoding() {
        decodingShouldFail(getCodec(InvalidGetterAndSetterModel.class), "{'integerField': 42, 'stringField': 'myString'}");
    }
}
