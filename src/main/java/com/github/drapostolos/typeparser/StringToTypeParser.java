package com.github.drapostolos.typeparser;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The purpose of this class is to parse a string (read from a file for example)
 * and convert it to a specific java object/Type. For example converting "1" to
 * an {@code Integer} type, or "1,2,3" to a {@code List<Integer>} type.
 */
public final class StringToTypeParser {
    private final Map<Type, TypeParser<?>> typeParsers;
    final Splitter splitter;
    final Splitter keyValuePairSplitter;

    /**
     * @return a new instance of {@link StringToTypeParserBuilder}
     */
    public static StringToTypeParserBuilder newBuilder() {
        return new StringToTypeParserBuilder();
    }

    StringToTypeParser(StringToTypeParserBuilder builder) {
        this.typeParsers = Collections.unmodifiableMap(new HashMap<Type, TypeParser<?>>(builder.typeParsers));
        this.splitter = builder.splitter;
        this.keyValuePairSplitter = builder.keyValuePairSplitter;
    }

    /**
     * Parses the given {@code input} string to the given {@code targetType}. 
     * 
     * @param input - string value to parse
     * @param targetType - the expected type to convert {@code input} to.
     * @return an instance of {@code targetType} corresponding to the given {@code input}.
     * @throws NullPointerException if any of the arguments are null.
     * @throws IllegalArgumentException if {@code input} is not parsable, or
     * if {@code type} is not recognized.
     */
    public <T> T parse(String input, Class<T> targetType) {
        if (input == null) {
            throw new NullPointerException(TypeParserUtility.makeNullArgumentErrorMsg("input"));
        }
        if (targetType == null) {
            throw new NullPointerException(TypeParserUtility.makeNullArgumentErrorMsg("targetType"));
        }
        
        @SuppressWarnings("unchecked")
        T temp = (T) parseType2(input, targetType);
        return temp;

    }

    /**
     * TODO
     * @param input
     * @param genericType
     * @return
     */
    public <T> T parse(String input, GenericType<T> genericType) {
        if (input == null) {
            throw new NullPointerException(TypeParserUtility.makeNullArgumentErrorMsg("input"));
        }
        if (genericType == null) {
            throw new NullPointerException(TypeParserUtility.makeNullArgumentErrorMsg("genericType"));
        }

        @SuppressWarnings("unchecked")
        T temp = (T) parseType2(input, genericType.getType());
        return temp;
    }

    /**
     * TODO
     * @param input
     * @param targetType
     * @return
     */
    public Object parseType(String input, Type targetType) {
        if (input == null) {
            throw new NullPointerException(TypeParserUtility.makeNullArgumentErrorMsg("input"));
        }
        if (targetType == null) {
            throw new NullPointerException(TypeParserUtility.makeNullArgumentErrorMsg("targetType"));
        }
        
        return parseType2(input, targetType);

    }
    
    private Object parseType2(String input, Type targetType) {
        if(typeParsers.containsKey(targetType)){
            return invokeTypeParser(input, targetType, targetType);
        } 

        if(targetType instanceof ParameterizedType){
            ParameterizedType type = (ParameterizedType) targetType;
            Class<?> rawType = (Class<?>) type.getRawType();
            if(List.class.isAssignableFrom(rawType)){
                return invokeTypeParser(input, TypeParsers.ANY_LIST, targetType);
            }
            if(Set.class.isAssignableFrom(rawType)){
                return invokeTypeParser(input, TypeParsers.ANY_SET, targetType);
            }
            if(Map.class.isAssignableFrom(rawType)){
                return invokeTypeParser(input, TypeParsers.ANY_MAP, targetType);
            }
        }
        
        if(targetType instanceof Class){
            Class<?> cls = (Class<?>) targetType;
            if(cls.isArray()){
                return invokeTypeParser(input, TypeParsers.ANY_ARRAY, targetType);
            }
            FactoryMethodInvoker invoker  = new FactoryMethodInvoker(cls);
            if(invoker.containsFactoryMethod()){
                return invoker.invokeFactoryMethod(input);
            }
        }
        
        /*
         * In java 1.6, when retrieving a methods parameter types through
         * reflection (using java.lang.reflect.Method#getGenericParameterTypes())
         * sometimes GenericArrayType is returned. Even if it is a regular array 
         * type (e.g. java.lang.String[]). The below is to handle this case.
         */
        if(targetType instanceof GenericArrayType){
            return invokeTypeParser(input, TypeParsers.ANY_ARRAY, targetType);
        }
        
        /*
         * If execution reaches here, it means there is no TypeParser for 
         * the given targetType. What remains is to make a descriptive error 
         * message and throw exception. 
         */
        String message = "There is either no registered 'TypeParser' for that type, or that "
                + "type does not contain the following static factory method: '%s.%s(String)'.";
        message = String.format(message, targetType, FactoryMethodInvoker.FACTORY_METHOD_NAME);
        message = TypeParserUtility.makeParseErrorMsg(input, message, targetType);
        throw new IllegalArgumentException(message);
    }

    private Object invokeTypeParser(String input, Type key, Type targetType) {
        /*
         * TODO
         * preProcess input? interface InputPreprocessor 
         */
        if (input.trim().equalsIgnoreCase("null")) {
            if (isPrimitive(targetType)) {
                String message = "'%s' primitive can not be set to null.";
                throw new IllegalArgumentException(String.format(message, targetType));
            }
            return null; 
        }

        try {
            TypeParser<?> typeParser = typeParsers.get(key);
            ParseHelper parseHelper = new ParseHelper(this, targetType);
            return typeParser.parse(input, parseHelper);
        } catch (NumberFormatException e) {
            String message =  String.format("Number format exception %s.", e.getMessage());
            message = TypeParserUtility.makeParseErrorMsg(input, message, targetType);
            throw new IllegalArgumentException(message, e);
        } catch (RuntimeException e) {
            String message = TypeParserUtility.makeParseErrorMsg(input, e.getMessage(),targetType);
            throw new IllegalArgumentException(message, e);
        }

    }

    private boolean isPrimitive(Type targetType) {
        if(targetType instanceof Class){
            Class<?> c = (Class<?>) targetType;
            return c.isPrimitive();
        }
        return false;
    }
}