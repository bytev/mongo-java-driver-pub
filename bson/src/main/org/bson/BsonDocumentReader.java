package org.bson;

import org.bson.types.Binary;
import org.bson.types.BsonArray;
import org.bson.types.BsonDocument;
import org.bson.types.BsonValue;
import org.bson.types.DBPointer;
import org.bson.types.ObjectId;
import org.bson.types.RegularExpression;
import org.bson.types.Timestamp;

import java.util.Iterator;
import java.util.Map;

public class BsonDocumentReader extends AbstractBsonReader {
    private BsonValue currentValue;

    public BsonDocumentReader(final BsonDocument document) {
        super(new BsonReaderSettings());
        setContext(new Context(null, BsonContextType.TOP_LEVEL, document));
        currentValue = document;
    }

    @Override
    protected Binary doReadBinaryData() {
        return currentValue.asBinary();
    }

    @Override
    protected boolean doReadBoolean() {
        return currentValue.asBoolean().getValue();
    }

    @Override
    protected long doReadDateTime() {
        return currentValue.asDateTime().getValue();
    }

    @Override
    protected double doReadDouble() {
        return currentValue.asDouble().getValue();
    }

    @Override
    protected void doReadEndArray() {
        setContext(getContext().getParentContext());
    }

    @Override
    protected void doReadEndDocument() {
        setContext(getContext().getParentContext());
        switch (getContext().getContextType()) {
            case ARRAY:
            case DOCUMENT:
                setState(State.TYPE);
                break;
            case TOP_LEVEL:
                setState(State.DONE);
                break;
            default:
                throw new BSONException("Unexpected ContextType.");
        }
    }

    @Override
    protected int doReadInt32() {
        return currentValue.asInt32().getValue();
    }

    @Override
    protected long doReadInt64() {
        return currentValue.asInt64().getValue();
    }

    @Override
    protected String doReadJavaScript() {
        return currentValue.asJavaScript().getCode();
    }

    @Override
    protected String doReadJavaScriptWithScope() {
        return currentValue.asJavaScriptWithScope().getCode();
    }

    @Override
    protected void doReadMaxKey() {
    }

    @Override
    protected void doReadMinKey() {
    }

    @Override
    protected void doReadNull() {
    }

    @Override
    protected ObjectId doReadObjectId() {
        return currentValue.asObjectId();
    }

    @Override
    protected RegularExpression doReadRegularExpression() {
        return currentValue.asRegularExpression();
    }

    @Override
    protected DBPointer doReadDBPointer() {
        return currentValue.asDBPointer();
    }

    @Override
    protected void doReadStartArray() {
        BsonArray array = currentValue.asArray();
        setContext(new Context(getContext(), BsonContextType.ARRAY, array));
    }

    @Override
    protected void doReadStartDocument() {
        BsonDocument document;
        if (currentValue.getBsonType() == BsonType.JAVASCRIPT_WITH_SCOPE) {
            document = currentValue.asJavaScriptWithScope().getScope();
        } else {
            document = currentValue.asDocument();
        }
        setContext(new Context(getContext(), BsonContextType.DOCUMENT, document));
    }

    @Override
    protected String doReadString() {
        return currentValue.asString().getValue();
    }

    @Override
    protected String doReadSymbol() {
        return currentValue.asSymbol().getSymbol();
    }

    @Override
    protected Timestamp doReadTimestamp() {
        return currentValue.asTimestamp();
    }

    @Override
    protected void doReadUndefined() {
    }

    @Override
    protected void doSkipName() {
    }

    @Override
    protected void doSkipValue() {
    }

    @Override
    public BsonType readBSONType() {
        if (getState() == State.INITIAL || getState() == State.SCOPE_DOCUMENT) {
            // there is an implied type of Document for the top level and for scope documents
            setCurrentBsonType(BsonType.DOCUMENT);
            setState(State.VALUE);
            return getCurrentBsonType();
        }

        switch (getContext().getContextType()) {
            case ARRAY:
                currentValue = getContext().getNextValue();
                if (currentValue == null) {
                    setState(State.END_OF_ARRAY);
                    return BsonType.END_OF_DOCUMENT;
                }
                setState(State.VALUE);
                break;
            case DOCUMENT:
                Map.Entry<String, BsonValue> currentElement = getContext().getNextElement();
                if (currentElement == null) {
                    setState(State.END_OF_DOCUMENT);
                    return BsonType.END_OF_DOCUMENT;
                }
                setCurrentName(currentElement.getKey());
                currentValue = currentElement.getValue();
                setState(State.NAME);
                break;
            default:
                throw new BSONException("Invalid ContextType.");
        }

        setCurrentBsonType(currentValue.getBsonType());
        return getCurrentBsonType();
    }

    @Override
    protected Context getContext() {
        return (Context) super.getContext();
    }

    protected static class Context extends AbstractBsonReader.Context {
        private Iterator<Map.Entry<String, BsonValue>> documentIterator;
        private Iterator<BsonValue> arrayIterator;

        protected Context(final Context parentContext, final BsonContextType contextType, final BsonArray array) {
            super(parentContext, contextType);
            arrayIterator = array.iterator();
        }

        protected Context(final Context parentContext, final BsonContextType contextType, final BsonDocument document) {
            super(parentContext, contextType);
            documentIterator = document.entrySet().iterator();
        }

        public Map.Entry<String, BsonValue> getNextElement() {
            if (documentIterator.hasNext()) {
                return documentIterator.next();
            } else {
                return null;
            }
        }

        public BsonValue getNextValue() {
            if (arrayIterator.hasNext()) {
                return arrayIterator.next();
            } else {
                return null;
            }
        }
    }
}