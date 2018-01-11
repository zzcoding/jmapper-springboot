package com.jmapper.core.util;

import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.DeserializationConfig.Feature;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;
import org.codehaus.jackson.type.TypeReference;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;


/**
 * @Author: Huiyong.Chang
 * @Date: 11:12 11/15/2014
 */
public final class JsonUtil {

    private static final Logger logger = Logger.getLogger(JsonUtil.class);

    private static ObjectMapper defaultMapper;

    static {
        defaultMapper = generateMapper(Inclusion.NON_EMPTY);
    }

    private JsonUtil() {}
    
    public static <T> List<T> toList(String json){
    	List<Object> list =null;
    	try {
    		list = defaultMapper.readValue(
				     json, List.class);
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return (List<T>) list;
    }
    
    
    public static List<LinkedHashMap<String, Object>> toListMap(String json){
    	List<LinkedHashMap<String, Object>> list =null;
    	try {
    		list = defaultMapper.readValue(
				     json, List.class);
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return list;
    }
    /**
     * 将JSON字符串转换成对象
     * @param json json字符串
     * @param clazz
     * @param <T>
     * @return
     */
    public static <T> T fromJson(String json, Class<T> clazz) {

        try {
            return clazz.equals(String.class) ? (T) json : defaultMapper.readValue(json, clazz);
        } catch (IOException e) {
            logger.error("对象转换错误: " + json);

            throw new RuntimeException("对象转换错误: " + json);
        }
    }

    /**
     * 将json通过类型转换成对象
     *
     * @param json json字符串
     * @param typeReference 引用类型
     * @return 返回对象
     * @param <T>
     */
    public static <T> T fromJson(String json, TypeReference<?> typeReference) {

        try {
            return (T) (typeReference.getType().equals(String.class) ? json : defaultMapper.readValue(json, typeReference));
        } catch (IOException e) {
            logger.error("对象转换错误: " + json);

            throw new RuntimeException("对象转换错误: " + json);
        }
    }

    /**
     * 将对象转换成json
     *
     * @param src
     * @param <T>
     * @return
     */
    public static <T> String toJson(T src) {

        try {
            return src instanceof String ? (String) src : defaultMapper.writeValueAsString(src);
        } catch (IOException e) {
            logger.error("对象转换JSON错误: " + src);

            throw new RuntimeException("对象转换JSON错误: " + src);
        }
    }

    /**
     * 将对象转换成json, 可以设置输出属性
     *
     * @param src 对象
     * @param inclusion 传入一个枚举值, 设置输出属性
     * @return 返回json字符串
     */
    public static <T> String toJson(T src, Inclusion inclusion) {

        try {
            if (src instanceof String) {
                return (String) src;
            } else {
                ObjectMapper customMapper = generateMapper(inclusion);
                return customMapper.writeValueAsString(src);
            }
        } catch (IOException e) {
            logger.error("对象转换JSON错误: " + src);

            throw new RuntimeException("对象转换JSON错误: " + src);
        }
    }

    /**
     * 将对象转换成json, 传入配置对象
     *
     * @param src 对象
     * @param mapper 配置对象
     * @return 返回json字符串
     */
    public static <T> String toJson(T src, ObjectMapper mapper) {

        try {
            if (null != mapper) {
                if (src instanceof String) {
                    return (String) src;
                } else {
                    return mapper.writeValueAsString(src);
                }
            } else {
                return null;
            }
        } catch (IOException e) {
            logger.error("对象转换JSON错误: " + src);

            throw new RuntimeException("对象转换JSON错误: " + src);
        }
    }

//    public static ObjectMapper getObjectMapper() {
//        return objectMapper;
//    }

    /**
     * 通过Inclusion创建ObjectMapper对象
     *
     * @param inclusion 传入一个枚举值, 设置输出属性
     * @return 返回ObjectMapper对象
     */
    private static ObjectMapper generateMapper(Inclusion inclusion) {

        ObjectMapper customMapper = new ObjectMapper();

        // 设置输出时包含属性的风格
        customMapper.setSerializationInclusion(inclusion);

        // 设置输入时忽略在JSON字符串中存在但Java对象实际没有的属性
        customMapper.configure(Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 禁止使用int代表Enum的order()來反序列化Enum,非常危險
        customMapper.configure(Feature.FAIL_ON_NUMBERS_FOR_ENUMS, true);

        // 所有日期格式都统一为以下样式
        customMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

        return customMapper;
    }
    
    public static void main(String[] args) {
    	ObjectMapper objectMapper =defaultMapper;
    	  String json = "[{\"uid\":1,\"uname\":\"www\",\"number\":234,\"upwd\":\"456\"},"
    			    + "{\"uid\":5,\"uname\":\"tom\",\"number\":3.44,\"upwd\":\"123\"}]";
    			  try {
    			   List<LinkedHashMap<String, Object>> list = objectMapper.readValue(
    			     json, List.class);
    			   System.out.println(list.size());
    			   for (int i = 0; i < list.size(); i++) {
    			    Map<String, Object> map = list.get(i);
    			    Set<String> set = map.keySet();
    			    for (Iterator<String> it = set.iterator(); it.hasNext();) {
    			     String key = it.next();
    			     System.out.println(key + ":" + map.get(key));
    			    }
    			   }
    			  } catch (JsonParseException e) {
    			   e.printStackTrace();
    			  } catch (JsonMappingException e) {
    			   e.printStackTrace();
    			  } catch (IOException e) {
    			   e.printStackTrace();
    			  }
	}

    public static Map<String, Object> objectToMap(Object obj) throws Exception {
        if (obj == null) {
            return null;
        }

        Map<String, Object> map = new HashMap<String, Object>();

        Field[] declaredFields = obj.getClass().getDeclaredFields();
        for (Field field : declaredFields) {
            field.setAccessible(true);
            map.put(field.getName(), field.get(obj));
        }

        return map;
    }
}