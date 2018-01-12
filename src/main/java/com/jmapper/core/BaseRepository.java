package com.jmapper.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import com.jmapper.core.mapper.ClassType;
import com.jmapper.core.mapper.KeyPropertyType;
import com.jmapper.core.mapper.PropertyType;
import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.log4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.IncorrectResultSetColumnCountException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.ReflectionUtils;

import com.jmapper.core.engine.MapperEngine;
import com.jmapper.core.exception.ServiceSupportException;
import com.jmapper.core.util.BigTableParameter;
import com.jmapper.core.util.PageModel;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * Function: 基础支持类. Project Name:jmapper-core <br>
 * File Name:ServiceSupport.java <br>
 * Package Name:com.jmapper.core <br>
 * Date:2016年4月25日下午1:39:54 <br>
 * Copyright (c) 2016, zinggozhao@163.com All Rights Reserved. <br>
 *
 * @author zhaoguang
 */
public class BaseRepository extends JdbcTemplate {

    Logger logger = Logger.getLogger(BaseRepository.class);
    private final BasicRowProcessor convert = new BasicRowProcessor();

    protected NamedParameterJdbcTemplate namedParameterJdbcTemplate;


    MapperEngine mapperEngine;


    /**
     * 持久化映射的实体类对象
     *
     * @param entity
     * @return
     */
    public <T> T save(T entity) {
        if (entity == null) {
            throw new ServiceSupportException("对象不能为空！");
        }
        GenerInsertSql generInsertSql = new GenerInsertSql<T>(entity).invoke();
        String insertSQL = generInsertSql.getInsertSQL();
        List<Object> params = generInsertSql.getParams();
        Field autoKeyfield = generInsertSql.getAutoKeyfield();
        Object idValue = generInsertSql.getIdValue();
        Object key = execute(new ConnectionCallback<Object>() {
            @Override
            public Object doInConnection(Connection con) throws SQLException, DataAccessException {
                PreparedStatement ps = con.prepareStatement(insertSQL, PreparedStatement.RETURN_GENERATED_KEYS);
                int parameterIndex = 1;
                for (Object param : params) {
                    ps.setObject(parameterIndex++, param);
                }
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    logger.debug(rs.getObject(1));
                    return rs.getObject(1);
                } else {
                    return null;
                }

            }
        });


        if (autoKeyfield != null && (idValue == null || (isWrapClass(idValue.getClass()) && idValue.toString().equals("0")))) {
            autoKeyfield.setAccessible(true);
            try {
                autoKeyfield.set(entity, key);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
        }
        return entity;
    }

    /**
     * 批量持久化映射的实体类对象
     *
     * @param entityList
     */
    public <T> void batchSave(List<T> entityList) {
        if (entityList == null || entityList.size() == 0) {
            throw new ServiceSupportException("entityList为空！");
        }
        T entity = entityList.get(0);
        String className = entity.getClass().getName();
        ClassType entityMapper = mapperEngine.getEntityCache().get(className);
        if (entityMapper == null) {
            throw new ServiceSupportException(className + "没有映射！");
        }

        String insertSQL = "";
        List<Object[]> paramList =new ArrayList<Object[]>();

        for (T e : entityList) {
            GenerInsertSql generInsertSql = new GenerInsertSql<T>(e).invoke();
            List<Object> params = generInsertSql.getParams();
            paramList.add(params.toArray());
            insertSQL=generInsertSql.getInsertSQL();
        }
        batchUpdate(insertSQL,paramList);

    }

    /**
     * 根据key 获得映射的实体类
     *
     * @param requiredType
     * @param key
     * @return
     * @throws ServiceSupportException
     */
    public <T> T getEntity(Class<T> requiredType, Object key) throws ServiceSupportException {
        String className = requiredType.getName();
        T entity = null;
        String keyColumnName = null;
        ClassType entityMapper = mapperEngine.getEntityCache().get(className);
        List<String> columns = new ArrayList<String>();
        List<String> wheres = new ArrayList<String>();
        List<Object> argList = new ArrayList<Object>();
        if (entityMapper == null) {
            throw new ServiceSupportException(className + "没有映射！");
        }
        if (entityMapper.getId() != null) {
            columns.add(entityMapper.getId().getColumn());
            wheres.add(entityMapper.getId().getColumn());
            argList.add(key);
        }
        if (entityMapper.getCompositeId() != null) {
            String clazz = entityMapper.getCompositeId().getClazz();
            List<KeyPropertyType> keyProperty = entityMapper.getCompositeId().getKeyProperty();
            for (KeyPropertyType keyPropertyType : keyProperty) {
                columns.add(keyPropertyType.getColumn());
                if (clazz.equals(key.getClass().getName())) {
                    wheres.add(keyPropertyType.getColumn());
                    Field field = ReflectionUtils.findField(key.getClass(), keyPropertyType.getName());
                    field.setAccessible(true);
                    argList.add(ReflectionUtils.getField(field, key));
                }
            }

        }
        for (PropertyType propertyType : entityMapper.getProperty()) {
            columns.add(propertyType.getColumn());
        }
        StringBuffer sql = new StringBuffer("select ");
        StringBuffer buffer = new StringBuffer();
        String selectCols = org.apache.commons.lang3.StringUtils.join(columns, ",");

        sql.append(selectCols)
                .append(" from ")
                .append(entityMapper.getTable())
                .append(" ")
                .append("where ");
        for (String where : wheres) {
            if (buffer.length() == 0) {
                buffer.append(" ")
                        .append(where + "=")
                        .append("?");
            } else {
                buffer.append(" and ")
                        .append(where + "=")
                        .append("?");
            }

        }
        sql.append(buffer);
        return queryForEntity(sql.toString(), requiredType, argList.toArray());
    }

    /**
     * 获得映射类型的所有实体类对象
     *
     * @param requiredType
     * @return
     */
    public <T> List<T> getAllEntity(Class<T> requiredType) {
        String className = requiredType.getName();
        T entity = null;
        String keyColumnName = null;
        ClassType entityMapper = mapperEngine.getEntityCache().get(className);
        List<String> columns = new ArrayList<String>();

        if (entityMapper == null) {
            throw new ServiceSupportException(className + "没有映射！");
        }
        if (entityMapper.getId() != null) {
            columns.add(entityMapper.getId().getColumn());

        }
        if (entityMapper.getCompositeId() != null) {
            String clazz = entityMapper.getCompositeId().getClazz();
            List<KeyPropertyType> keyProperty = entityMapper.getCompositeId().getKeyProperty();
            for (KeyPropertyType keyPropertyType : keyProperty) {
                columns.add(keyPropertyType.getColumn());

            }

        }
        for (PropertyType propertyType : entityMapper.getProperty()) {
            columns.add(propertyType.getColumn());
        }
        StringBuffer sql = new StringBuffer("select ");
        StringBuffer buffer = new StringBuffer();
        String selectCols = org.apache.commons.lang3.StringUtils.join(columns, ",");

        sql.append(selectCols)
                .append(" from ")
                .append(entityMapper.getTable())
                .append(" ");

        return queryForEntityList(sql.toString(), requiredType);
    }

    /**
     * 查询sql为Map集合 例如：select id as userId,name as userName from t_user
     * userId,userName即为map中的键，注意只能查询一个结果集
     *
     * @param sql
     * @param args
     * @return
     */
    public Map<String, Object> queryForMap(String sql, Object... args) {
        Map<String, Object> resultMap = null;
        try {
            resultMap = queryForMap(sql, args);
        } catch (IncorrectResultSizeDataAccessException e) {
            logger.debug("queryForMapSimple未查询到唯一结果集！");
        } catch (IncorrectResultSetColumnCountException e) {
            logger.debug("queryForMapSimple只能够以一列为结果集！");
        }

        return resultMap;
    }

    /**
     * 根据sql映射mapper查询集合，例如 queryForMapSimpleByMapper（"sqlmapper.crm.user",10）
     *
     * @param mapper
     * @param args
     * @return
     */
    public Map<String, Object> queryForMapByMapper(String mapper, Object... args) {
        Map<String, Object> resultMap = null;
        try {

            Configuration cfg = mapperEngine.getCfg();
            Template template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(null, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            logger.debug(resultSql);
            resultMap = queryForMap(resultSql, args);
        } catch (IncorrectResultSizeDataAccessException e) {
            logger.debug("queryForMapSimpleByMapper未查询到唯一结果集！");
        } catch (IncorrectResultSetColumnCountException e) {
            logger.debug("queryForMapSimpleByMapper只能够以一列为结果集！");
        } catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resultMap;
    }

    /**
     * 根据sql映射mapper，命名参数查询唯一Map。
     *
     * @param mapper
     * @param parameterMap
     * @return
     */
    public Map<String, Object> queryForMapNamedParameterByMapper(String mapper, Map<String, Object> parameterMap) {
        Map<String, Object> resultMap = null;
        try {
            Configuration cfg = mapperEngine.getCfg();
            Template template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(parameterMap, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            logger.debug(resultSql);
            resultMap = namedParameterJdbcTemplate.queryForMap(resultSql, parameterMap);
        } catch (IncorrectResultSizeDataAccessException e) {
            logger.debug("queryForMapNamedParameterByMapper未查询到唯一结果集！");
        } catch (IncorrectResultSetColumnCountException e) {
            logger.debug("queryForMapNamedParameterByMapper只能够以一列为结果集！");
        } catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return resultMap;
    }

    /**
     * 普通命名参数查询唯一Map。
     *
     * @param mapper
     * @param parameterMap
     * @return
     */
    public Map<String, Object> queryForMapNamedParameter(String mapper, Map<String, Object> parameterMap) {
        Map<String, Object> resultMap = null;
        try {

            resultMap = namedParameterJdbcTemplate.queryForMap(mapper, parameterMap);
        } catch (IncorrectResultSizeDataAccessException e) {
            logger.debug("queryForMapNamedParameter未查询到唯一结果集！");
        } catch (IncorrectResultSetColumnCountException e) {
            logger.debug("queryForMapNamedParameter只能够以一列为结果集！");
        }

        return resultMap;
    }

    /**
     * 根据sql映射mapper查询实体类集合，只能查询一个字段
     * 例：queryForListSimpleByMapper（"sqlmapper.user",String.class,"zhang"）
     *
     * @param mapper
     * @param requiredType
     * @param args
     * @return
     */
    public <T> List<T> queryForListSimpleByMapper(String mapper, Class<T> requiredType, Object... args) {
        Configuration cfg = mapperEngine.getCfg();
        Template template;
        List<T> resultList = null;
        try {
            template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(null, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            logger.debug(resultSql);
            resultList = queryForList(resultSql, requiredType, args);
        } catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resultList;
    }

    /**
     * 根据sql映射mapperming命名参数查询实体类集合，只能查询一个字段
     * 例：queryForListSimpleByMapper（"sqlmapper.user",String.class,parameterMap）
     *
     * @param mapper
     * @param requiredType
     * @param parameterMap
     * @return
     */
    public <T> List<T> queryForListNamedParameterByMapper(String mapper, Class<T> requiredType,
                                                          Map<String, Object> parameterMap) {
        Configuration cfg = mapperEngine.getCfg();
        Template template;
        List<T> resultList = null;
        try {
            template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(parameterMap, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            logger.debug(resultSql);
            resultList = namedParameterJdbcTemplate.queryForList(resultSql, parameterMap, requiredType);
        } catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resultList;
    }

    /*   
     *   
     * @author zhaoguang  
     * @date 2018/1/6 23:08
     * @description 
     * 
     */
    public int queryForInt(String sql, Object... args) {
        return queryForObject(sql, Integer.class, args);
    }

    /**
     * 根据sql，命名参数查询 List Map结果集
     *
     * @param sql
     * @param parameterMap
     * @return
     */
    public List<Map<String, Object>> queryForListMapNamedParameter(String sql, Map<String, ?> parameterMap) {
        return namedParameterJdbcTemplate.queryForList(sql, parameterMap);
    }

    /**
     * 根据sqlmapper，查询 List Map结果集
     *
     * @param mapper
     * @param args
     * @return
     */
    public List<Map<String, Object>> queryForListMapByMapper(String mapper, Object... args) {
        Configuration cfg = mapperEngine.getCfg();
        List<Map<String, Object>> resultList = null;
        Template template;
        try {
            template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(null, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            logger.debug(resultSql);
            resultList = queryForList(resultSql, args);
        } catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return resultList;
    }

    /**
     * 根据sqlmapper 命名参数查询 List Map结果集
     *
     * @param mapper
     * @param parameterMap
     * @return
     */
    public List<Map<String, Object>> queryForListMapNamedParameterByMapper(String mapper, Map<String, ?> parameterMap) {
        Configuration cfg = mapperEngine.getCfg();
        List<Map<String, Object>> resultList = null;
        Template template;
        try {
            template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(parameterMap, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            logger.debug(resultSql);
            resultList = namedParameterJdbcTemplate.queryForList(resultSql, parameterMap);
        } catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return resultList;
    }

    /**
     * 根据sqlmapper执行sql语句
     *
     * @param mapper
     * @param args
     * @return
     */
    public int executeUpdateByMapper(String mapper, Object... args) {
        Configuration cfg = mapperEngine.getCfg();
        int effCount = 0;
        Template template;
        try {
            template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(null, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            logger.debug(resultSql);
            effCount = update(resultSql, args);
        } catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return effCount;
    }

    /**
     * 根据sqlmapper，命名参数执行sql语句
     *
     * @param mapper
     * @param parameterMap
     * @return
     */
    public int executeUpdateNamedParameterByMapper(String mapper, Map<String, ?> parameterMap) {
        Configuration cfg = mapperEngine.getCfg();
        int effCount = 0;
        Template template;
        try {
            template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(parameterMap, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            logger.debug(resultSql);
            effCount = namedParameterJdbcTemplate.update(resultSql, parameterMap);
        } catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return effCount;
    }

    /**
     * 根据sql，命名参数执行sql语句
     *
     * @param sql
     * @param parameterMap
     * @return
     */
    public int executeUpdateNamedParameterBySql(String sql, Map<String, ?> parameterMap) {
        int effCount = 0;

        sql = removeBlank(sql);
        logger.debug(sql);
        effCount = namedParameterJdbcTemplate.update(sql, parameterMap);

        return effCount;
    }

    /**
     * 根据sql语句，对象项类型查询唯一结果集
     *
     * @param sql
     * @param requiredType
     * @param args
     * @return
     */
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        T t = null;
        try {
            t = queryForObject(sql, requiredType, args);
        } catch (IncorrectResultSizeDataAccessException e) {
            logger.debug("queryForObject未查询到唯一结果集！");
        } catch (IncorrectResultSetColumnCountException e) {
            logger.debug("queryForObject只能够以一列为结果集");
        }
        return t;
    }

    /**
     * 根据sqlmapper,对象项类型查询唯一结果集
     *
     * @param mapper
     * @param requiredType
     * @param args
     * @return
     */
    public <T> T queryForObjectByMapper(String mapper, Class<T> requiredType, Object... args) {
        T t = null;
        Configuration cfg = mapperEngine.getCfg();
        Template template;
        try {
            template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(null, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            logger.debug(resultSql);
            t = queryForObject(resultSql, requiredType, args);
        } catch (IncorrectResultSizeDataAccessException e) {
            logger.debug("queryForObjectByMapper未查询到唯一结果集！");
        } catch (IncorrectResultSetColumnCountException e) {
            logger.debug("queryForObjectByMapper只能够以一列为结果集");
        } catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return t;
    }

    /**
     * 根据sqlmapper 查询int结果集
     *
     * @param mapper
     * @param args
     * @return
     */
    public int queryForIntByMapper(String mapper, Object... args) {
        int count = 0;
        Configuration cfg = mapperEngine.getCfg();
        Template template;
        try {
            template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(null, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            logger.debug(resultSql);
            count = this.queryForInt(resultSql, args);
        } catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return count;
    }

    /**
     * 根据sqlmapper，命名参数查询int结果集
     *
     * @param mapper
     * @param parameterMap
     * @return
     */
    public int queryForIntNamedParameterByMapper(String mapper, Map<String, Object> parameterMap) {
        int count = 0;
        Configuration cfg = mapperEngine.getCfg();
        Template template;
        try {
            template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(parameterMap, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            logger.debug(resultSql);
            count = queryForIntNamedParameter(resultSql, parameterMap);
        } catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return count;
    }

    /**
     * 根据sqlmapper 获得sql语句
     *
     * @param mapper
     * @return
     */
    public String getTemplateSql(String mapper) {
        String resultSql = null;
        try {
            Configuration cfg = mapperEngine.getCfg();
            Template template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(null, new OutputStreamWriter(baos));
            resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            logger.debug(resultSql);

        } catch (Exception e) {
            logger.debug(e);
        }
        return resultSql;

    }

    /**
     * 根据sql命名参数查询int结果集
     *
     * @param countSql
     * @param parameterMap
     * @return
     */
    public int queryForIntNamedParameter(String countSql, Map<String, Object> parameterMap) {
        int totalCount = 0;
        try {
            totalCount = namedParameterJdbcTemplate.queryForObject(countSql, parameterMap, Integer.class);
        } catch (IncorrectResultSizeDataAccessException e) {
            logger.debug("queryForIntNamedParameter未查询到唯一结果集！");
        }
        return totalCount;

    }

    /**
     * 根据sqlmapper 命名参数获得sql语句
     *
     * @param mapper
     * @param paramaterMap
     * @return
     */
    public String getTemplateSql(String mapper, Map<String, Object> paramaterMap) {
        String resultSql = null;
        try {
            Configuration cfg = mapperEngine.getCfg();
            Template template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(paramaterMap, new OutputStreamWriter(baos));
            resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            logger.debug(resultSql);

        } catch (Exception e) {
            logger.debug(e);
        }
        return resultSql;

    }

    /**
     * 根据sqlmapper 命名参数分页查询List Map结果集
     *
     * @param mapper
     * @param pageModel
     * @param parameterMap
     * @return
     */
    public List<Map<String, Object>> queryForPageListMapNamedParameterByMapper(String mapper, PageModel pageModel,
                                                                               Map<String, Object> parameterMap) {
        List<Map<String, Object>> resultList = null;
        try {
            Configuration cfg = mapperEngine.getCfg();
            Template template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(parameterMap, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            String countSql = getCountSqlFromOrgSql(mapper, resultSql, parameterMap);
            int totalCount = queryForIntNamedParameter(countSql, parameterMap);
            pageModel.setRecordCount(totalCount);
            if (totalCount == 0) {
                return new ArrayList<Map<String, Object>>();
            }
            resultSql = resultSql + " limit :startrow,:pageSize";
            logger.warn(resultSql);
            parameterMap.put("startrow", pageModel.getStartRow());
            parameterMap.put("pageSize", pageModel.getPageSize());
            resultList = namedParameterJdbcTemplate.queryForList(resultSql, parameterMap);
        } catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resultList;
    }

    /**
     * 根据sqlmapper ，分页查询List Map结果集
     *
     * @param mapper
     * @param pageModel
     * @param args
     * @return
     */
    public List<Map<String, Object>> queryForPageListMapByMapper(String mapper, PageModel pageModel,
                                                                 Object... args) {
        List<Map<String, Object>> resultList = null;
        try {
            Configuration cfg = mapperEngine.getCfg();
            Template template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(null, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            String countSql = getCountSqlFromOrgSql(mapper, resultSql);
            int totalCount = this.queryForInt(countSql, args);
            pageModel.setRecordCount(totalCount);
            if (totalCount == 0) {
                return new ArrayList<Map<String, Object>>();
            }
            resultSql = resultSql + " limit ?,?";
            logger.warn(resultSql);
            ArrayList<Object> params = new ArrayList<Object>();
            for (Object arg : args) {
                params.add(arg);
            }
            params.add(pageModel.getStartRow());
            params.add(pageModel.getPageSize());
            resultList = queryForList(resultSql, params.toArray());
        } catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resultList;
    }

    /**
     * 根据sqlmapper获得分页count的sql语句
     *
     * @param mapper
     * @param resultSql
     * @return
     */
    private String getCountSqlFromOrgSql(String mapper, String resultSql) {

        String templateSql = getTemplateSql(mapper + ".count");
        String countSql = "";
        if (templateSql == null) {
            int formIndex = resultSql.indexOf("from") == -1 ? resultSql.indexOf("FROM") : resultSql.indexOf("from");
            countSql = resultSql.substring(formIndex);
            // int orderIndex = countSql.indexOf("order") == -1 ?
            // countSql.indexOf("ORDER") : countSql.indexOf("order");
            // String removeOrder = orderIndex == -1 ? countSql :
            // countSql.substring(0, orderIndex);
            countSql = "select count(1) " + countSql;
        } else {
            countSql = templateSql;
        }
        return countSql;
    }

    /**
     * 根据sqlmapper命名参数获得分页count的sql语句
     *
     * @param mapper
     * @param resultSql
     * @param parameterMap
     * @return
     */
    private String getCountSqlFromOrgSql(String mapper, String resultSql, Map<String, Object> parameterMap) {

        String templateSql = getTemplateSql(mapper + ".count", parameterMap);
        String countSql = "";
        if (templateSql == null) {
            int formIndex = resultSql.indexOf("from") == -1 ? resultSql.indexOf("FROM") : resultSql.indexOf("from");
            countSql = resultSql.substring(formIndex);
            // int orderIndex = countSql.indexOf("order") == -1 ?
            // countSql.indexOf("ORDER") : countSql.indexOf("order");
            // String removeOrder = orderIndex == -1 ? countSql :
            // countSql.substring(0, orderIndex);
            // countSql = "select count(1) " + removeOrder;
            countSql = "select count(1) " + countSql;
        } else {
            countSql = templateSql;
        }
        return countSql;
    }

    /**
     * 根据sql 实体类类型获得分页的实体类集合
     *
     * @param mapper
     * @param requiredType
     * @param pageModel
     * @param args
     * @return
     */

    public <T> List<T> queryForPageEntityListByMapper(String mapper, Class<T> requiredType, PageModel pageModel,
                                                      Object... args) {
        List<T> resultList = null;
        try {
            Configuration cfg = mapperEngine.getCfg();
            Template template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(null, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            String countSql = getCountSqlFromOrgSql(mapper, resultSql);
            int totalCount = this.queryForInt(countSql, args);
            pageModel.setRecordCount(totalCount);
            if (totalCount == 0) {
                return (List<T>) new ArrayList<T>();
            }
            resultSql = resultSql + " limit ?,?";
            logger.warn(resultSql);
            ArrayList<Object> params = new ArrayList<Object>();
            for (Object arg : args) {
                params.add(arg);
            }
            params.add(pageModel.getStartRow());
            params.add(pageModel.getPageSize());
            resultList = queryForEntityList(resultSql, requiredType, params.toArray());
            ;
        } catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resultList;
    }

    /**
     * 根据sqlmapper 实体类类型获得分页的实体类集合
     *
     * @param mapper
     * @param requiredType
     * @param pageModel
     * @param parameterMap
     * @return
     */
    public <T> List<T> queryForPageEntityListNamedParameterByMapper(String mapper, Class<T> requiredType,
                                                                    PageModel pageModel, Map<String, Object> parameterMap) {
        List<T> resultList = null;
        try {
            Configuration cfg = mapperEngine.getCfg();
            Template template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(parameterMap, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            String countSql = getCountSqlFromOrgSql(mapper, resultSql, parameterMap);
            int totalCount = queryForIntNamedParameter(countSql, parameterMap);
            pageModel.setRecordCount(totalCount);
            if (totalCount == 0) {
                return (List<T>) new ArrayList<T>();
            }
            resultSql = resultSql + " limit :startrow,:pageSize";
            logger.warn(resultSql);
            parameterMap.put("startrow", pageModel.getStartRow());
            parameterMap.put("pageSize", pageModel.getPageSize());
            resultList = queryForEntityListNamedParameter(resultSql, requiredType, parameterMap);
        } catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resultList;
    }

    /**
     * 根据sql语句，实体类类型，命名参数查询实体类集合
     *
     * @param sql
     * @param requiredType
     * @param parameterMap
     * @return
     */
    public <T> List<T> queryForEntityListNamedParameter(String sql, final Class<T> requiredType,
                                                        Map<String, Object> parameterMap) {

        List<T> resultList = null;
        try {

            BeanPropertyRowMapper<T> rowMapper = new BeanPropertyRowMapper<T>(requiredType);
            resultList = namedParameterJdbcTemplate.query(sql, parameterMap, rowMapper);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultList;
    }

    /**
     * 根据sql语句，实体类类型，查询实体类集合
     *
     * @param sql
     * @param requiredType
     * @param args
     * @return
     */
    public <T> List<T> queryForEntityList(String sql, final Class<T> requiredType, Object... args) {

        List<T> resultList = null;
        try {

            BeanPropertyRowMapper<T> rowMapper = new BeanPropertyRowMapper<T>(requiredType);
            resultList = query(sql, rowMapper, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultList;
    }

    /**
     * 根据sqlmapper、实体类类型查询实体类集合
     *
     * @param mapper
     * @param requiredType
     * @param args
     * @return
     */
    public <T> List<T> queryForEntityListByMapper(String mapper, final Class<T> requiredType, Object... args) {
        Configuration cfg = mapperEngine.getCfg();
        Template template;
        List<T> resultList = null;
        try {
            template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(null, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            BeanPropertyRowMapper<T> rowMapper = new BeanPropertyRowMapper<T>(requiredType);
            resultList = query(resultSql, rowMapper, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultList;

    }

    /**
     * 根据sql语句，实体类类型，命名参数查询实体类集合
     *
     * @param sql
     * @param requiredType
     * @param parameterMap
     * @return
     */
    public <T> T queryForEntityNamedParameter(String sql, final Class<T> requiredType,
                                              Map<String, Object> parameterMap) {
        T obj = null;

        try {
            BeanPropertyRowMapper<T> rowMapper = new BeanPropertyRowMapper<T>(requiredType);
            obj = namedParameterJdbcTemplate.queryForObject(sql, parameterMap, rowMapper);

        } catch (EmptyResultDataAccessException e) {
            logger.debug("queryForEntity未查询到");
        }
        return obj;
    }

    /**
     * 根据sql语句，实体类类型查询实体类集合
     *
     * @param sql
     * @param requiredType
     * @param args
     * @return
     */
    public <T> T queryForEntity(String sql, final Class<T> requiredType, Object... args) {
        T obj = null;
        try {
            BeanPropertyRowMapper<T> rowMapper = new BeanPropertyRowMapper<T>(requiredType);
            obj = queryForObject(sql, rowMapper, args);
        } catch (EmptyResultDataAccessException e) {
            logger.debug("queryForEntity未查询到");
        }
        return obj;
    }

    /**
     * sqlmapper，实体类类型查询实体类集合
     *
     * @param mapper
     * @param requiredType
     * @param args
     * @return
     */
    public <T> T queryForEntityByMapper(String mapper, final Class<T> requiredType, Object... args) {
        T obj = null;
        Configuration cfg = mapperEngine.getCfg();
        Template template;
        try {
            template = cfg.getTemplate(mapper, "utf-8");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.process(null, new OutputStreamWriter(baos));
            String resultSql = baos.toString();
            resultSql = removeBlank(resultSql);
            BeanPropertyRowMapper<T> rowMapper = new BeanPropertyRowMapper<T>(requiredType);
            obj = queryForObject(resultSql, rowMapper);

        } catch (Exception e) {
            e.printStackTrace();
            logger.debug("queryForEntity未查询到");
        }
        return obj;
    }


    public void extractBigTable(final String sql, BigTableParameter parameters) {
        query(new PreparedStatementCreator() {

            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                PreparedStatement prepareStatement = con.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY);
                prepareStatement.setFetchDirection(ResultSet.FETCH_REVERSE);
                prepareStatement.setFetchSize(Integer.MIN_VALUE);
                return prepareStatement;
            }
        }, parameters);
    }

    /**
     * 去掉sql语句中的回车换行
     *
     * @param sql
     * @return
     */
    public String removeBlank(String sql) {
        String resultSql = "";
        Pattern p = Pattern.compile("\r|\n|\t");
        Matcher m = p.matcher(sql);
        resultSql = m.replaceAll(" ");
        resultSql = resultSql.trim();
        resultSql = resultSql.replaceAll("\\s{1,}", " ");
        return resultSql;
    }

    public boolean isWrapClass(Class clz) {
        try {
            return ((Class) clz.getField("TYPE").get(null)).isPrimitive();
        } catch (Exception e) {
            return false;
        }
    }


    public int[] batchUpdate(String sql, Map<String, ?>[] batchValues) {
        return namedParameterJdbcTemplate.batchUpdate(sql, batchValues);
    }

    public MapperEngine getMapperEngine() {
        return mapperEngine;
    }

    public void setMapperEngine(MapperEngine mapperEngine) {
        this.mapperEngine = mapperEngine;
    }

    public BaseRepository(MapperEngine mapperEngine, DataSource dataSource) {
        super(dataSource);
        this.mapperEngine = mapperEngine;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }


    private class GenerInsertSql<T> {
        private T entity;
        private Field autoKeyfield;
        private List<Object> params;
        private Object idValue;
        private String insertSQL;

        public GenerInsertSql(T entity) {
            this.entity = entity;
        }

        public Field getAutoKeyfield() {
            return autoKeyfield;
        }

        public List<Object> getParams() {
            return params;
        }

        public Object getIdValue() {
            return idValue;
        }

        public String getInsertSQL() {
            return insertSQL;
        }

        public GenerInsertSql invoke() {
            autoKeyfield = null;
            String className = entity.getClass().getName();
            ClassType entityMapper = mapperEngine.getEntityCache().get(className);
            if (entityMapper == null) {
                throw new ServiceSupportException(className + "没有映射！");
            }
            logger.debug(entityMapper.getTable());
            params = new ArrayList<Object>();
            List<PropertyType> propertyTypes = new  ArrayList<PropertyType>();

            StringBuffer buffer = new StringBuffer("insert into ");
            buffer.append(entityMapper.getTable());
            buffer.append("( ");
            StringBuffer feildBuffer = new StringBuffer("");
            StringBuffer valueBuffer = new StringBuffer("");
            idValue = null;
            if (entityMapper.getId() != null) {
                try {
                    autoKeyfield = entity.getClass().getDeclaredField(entityMapper.getId().getName());
                    autoKeyfield.setAccessible(true);
                    idValue = autoKeyfield.get(entity);
                    if (autoKeyfield != null && idValue != null) {
                        PropertyType propertyType = new PropertyType();
                        propertyType.setColumn(entityMapper.getId().getColumn());
                        propertyType.setName(entityMapper.getId().getName());
                        propertyType.setValue(entityMapper.getId().getValue());
                        propertyTypes.add(propertyType);
                    }


                } catch (SecurityException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            if (entityMapper.getCompositeId() != null) {
                List<KeyPropertyType> keyProperty = entityMapper.getCompositeId().getKeyProperty();
                for (KeyPropertyType keyPropertyType : keyProperty) {
                    PropertyType propertyType = new PropertyType();
                    propertyType.setColumn(keyPropertyType.getColumn());
                    propertyType.setName(keyPropertyType.getName());
                    propertyType.setValue(keyPropertyType.getValue());
                    propertyTypes.add(propertyType);
                }
            }
            propertyTypes.addAll(entityMapper.getProperty());
            for (PropertyType ep : propertyTypes ) {

                if (feildBuffer.length() == 0) {
                    feildBuffer.append(ep.getColumn());
                } else {
                    feildBuffer.append(",");
                    feildBuffer.append(ep.getColumn());
                }
                if (valueBuffer.length() == 0) {
                    valueBuffer.append("?");
                    Field field;
                    try {
                        field = entity.getClass().getDeclaredField(ep.getName());
                        field.setAccessible(true);
                        params.add(ReflectionUtils.getField(field, entity));
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    } catch (NoSuchFieldException e) {
                        e.printStackTrace();
                    }

                } else {
                    valueBuffer.append(",?");
                    Field field;
                    try {
                        field = entity.getClass().getDeclaredField(ep.getName());
                        field.setAccessible(true);
                        params.add(ReflectionUtils.getField(field, entity));
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    } catch (NoSuchFieldException e) {
                        e.printStackTrace();
                    }

                }

            }
            buffer.append(feildBuffer).append(" ) values ( ");
            buffer.append(valueBuffer).append(" ) ");
            logger.debug(buffer.toString());
            insertSQL = buffer.toString();
            return this;
        }
    }
}
