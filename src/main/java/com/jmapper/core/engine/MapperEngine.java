package com.jmapper.core.engine;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import com.jmapper.core.exception.MappingException;
import com.jmapper.core.mapper.*;
import com.jmapper.core.util.JaxbUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;


import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 映射引擎，容器加载时读取映射文件.
 * <p>
 * <p>
 * User:zhaoguang
 * <p>
 * Date:2016年6月26日
 * <p>
 * Version: 1.0
 */
public class MapperEngine {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private String entity;
    private String sql;
    private Map<String, ClassType> entityCache = new HashMap<String, ClassType>();
    private Map<String, String> sqlTemplateCache = new HashMap<String, String>();
    private Configuration cfg = new Configuration(Configuration.VERSION_2_3_23);
    private StringTemplateLoader stringLoader = new StringTemplateLoader();
    private Map<String, HashMap<String, String>> xmlMappedFields;

    public Map<String, HashMap<String, String>> getXmlMappedFields() {
        return xmlMappedFields;
    }

    public void setXmlMappedFields(Map<String, HashMap<String, String>> xmlMappedFields) {
        this.xmlMappedFields = xmlMappedFields;
    }

    public void initSqlMapper(List<File> fileList) throws Exception {
        parseSqlMapper(fileList);
    }

    public void parseSqlMapper(List<File> fileList) throws Exception {

        for (File file : fileList) {

            String xml = FileUtils.readFileToString(file, "UTF-8");
            logger.info("################开始缓存sql->" + file.getName());
            JaxbUtil resultBinder = new JaxbUtil(SqlTemplateMapper.class, JaxbUtil.CollectionWrapper.class);
            SqlTemplateMapper mapper = resultBinder.fromXml(xml);
            String namespace = mapper.getNamespace();
            logger.info("################mapper namespace->" + namespace);
            for (SqlMapper sql : mapper.getSql()) {
                String cached = sqlTemplateCache.get(namespace + "." + sql.getId());
                if (StringUtils.isBlank(sql.getId())) {
                    throw new MappingException("【" + file.getName() + "】中sql mapper id不能为空!");
                }
                if (cached != null) {
                    throw new MappingException(namespace + "." + sql.getId() + "在sqlTemplateCache中已存在！");
                } else {
                    sqlTemplateCache.put(namespace + "." + sql.getId(), sql.getData());
                    stringLoader.putTemplate(namespace + "." + sql.getId(), sql.getData());
                }
            }
            cfg.setTemplateLoader(stringLoader);
        }
    }

    public void initEntityMapper(List<File> fileList) throws Exception {

        parseEntityMapper(fileList);
    }

    public void parseEntityMapper(List<File> fileList) throws Exception {
        for (File file : fileList) {

            String xml = FileUtils.readFileToString(file, "UTF-8");
            logger.info("################开始缓存entity->" + file.getName());
            JaxbUtil resultBinder = new JaxbUtil(HibernateMappingType.class, JaxbUtil.CollectionWrapper.class);
            HibernateMappingType mapper = resultBinder.fromXml(xml);
            logger.info("################mapper -->" + mapper.getClazz().getName());
            if (StringUtils.isBlank(mapper.getClazz().getName())) {
                throw new MappingException("【" + file.getName() + "】中entity mapper name不能为空!");
            }
            ClassType cached = entityCache.get(mapper.getClazz().getName());
            if (cached != null) {
                throw new MappingException(mapper.getClazz().getName() + "在entityCache中已存在！");
            } else {
                initGlobalPropertyColumnMapping(mapper.getClazz());
                entityCache.put(mapper.getClazz().getName(), mapper.getClazz());
            }
        }
    }

    //初始化所有对象字段和表字段映射
    public void initGlobalPropertyColumnMapping(ClassType classType) {
        if (this.xmlMappedFields == null)
            this.xmlMappedFields = new HashMap<String, HashMap<String, String>>();
        HashMap<String, String> fieldsMap = new HashMap<String, String>();
        String name = classType.getName();
        if (classType.getId() != null) {
            fieldsMap.put(classType.getId().getName(), classType.getId().getColumn());
        }
        if (classType.getCompositeId() != null) {
            String keyclazz = classType.getCompositeId().getClazz();
            List<KeyPropertyType> keyProperty = classType.getCompositeId().getKeyProperty();
            for (KeyPropertyType keyPropertyType : keyProperty) {
                fieldsMap.put(keyPropertyType.getName(), keyPropertyType.getColumn());
            }
        }
        for (PropertyType propertyType : classType.getProperty()) {
            fieldsMap.put(propertyType.getName(), propertyType.getColumn());
        }
        xmlMappedFields.put(name, fieldsMap);
    }

    // 读取类路径下所有实体类映射文件
    private void findEntityMapperFile(File file, List<File> fileList) {

        for (File sfile : file.listFiles()) {
            if (sfile.isDirectory()) {
                findEntityMapperFile(sfile, fileList);
            } else {
                if (FilenameUtils.getExtension(sfile.getName()).equals("xml")) {
                    Pattern pattern = Pattern.compile(entity);
                    Matcher matcher = pattern.matcher(sfile.getName());
                    if (matcher.matches()) {
                        fileList.add(sfile);
                    }

                }
            }
        }

    }

    // 读取类路径下所有sql映射文件
    private void findSqlMapperFile(File file, List<File> fileList) {

        for (File sfile : file.listFiles()) {
            if (sfile.isDirectory()) {
                findSqlMapperFile(sfile, fileList);
            } else {
                if (FilenameUtils.getExtension(sfile.getName()).equals("xml")) {
                    Pattern pattern = Pattern.compile(sql);
                    Matcher matcher = pattern.matcher(sfile.getName());
                    if (matcher.matches()) {
                        fileList.add(sfile);
                    }

                }
            }
        }

    }

    public Map<String, ClassType> getEntityCache() {
        return entityCache;
    }

    public void setEntityCache(Map<String, ClassType> entityCache) {
        this.entityCache = entityCache;
    }

    public Map<String, String> getSqlTemplateCache() {
        return sqlTemplateCache;
    }

    public void setSqlTemplateCache(Map<String, String> sqlTemplateCache) {
        this.sqlTemplateCache = sqlTemplateCache;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public Configuration getCfg() {
        return cfg;
    }

    public void setCfg(Configuration cfg) {
        this.cfg = cfg;
    }

    public StringTemplateLoader getStringLoader() {
        return stringLoader;
    }

    public void setStringLoader(StringTemplateLoader stringLoader) {
        this.stringLoader = stringLoader;
    }

}
