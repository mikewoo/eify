package com.eify.common.log;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SQL 日志自动配置
 *
 * <p>自动配置 MyBatis SQL 日志拦截器
 *
 * @author Claude
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(SqlSessionFactory.class)
@EnableConfigurationProperties(SqlLogProperties.class)
public class SqlLogAutoConfiguration {

    @Autowired
    private SqlLogProperties sqlLogProperties;

    /**
     * 初始化 SQL 日志拦截器
     */
    @Bean
    public SqlLogAutoConfigurationInitializer sqlLogInterceptor(
            @Autowired(required = false) List<SqlSessionFactory> sqlSessionFactoryList) {

        if (sqlSessionFactoryList == null || sqlSessionFactoryList.isEmpty()) {
            log.warn("No SqlSessionFactory found, SQL logging interceptor not registered");
            return new SqlLogAutoConfigurationInitializer();
        }

        SqlLogInterceptor interceptor = new SqlLogInterceptor();

        // 从配置文件中读取配置
        java.util.Properties props = new java.util.Properties();
        props.setProperty("logEnabled", String.valueOf(sqlLogProperties.isEnabled()));
        props.setProperty("slowQueryThresholdMillis", String.valueOf(sqlLogProperties.getSlowQueryThreshold()));
        props.setProperty("maxSqlLength", String.valueOf(sqlLogProperties.getMaxSqlLength()));
        props.setProperty("samplingRate", String.valueOf(sqlLogProperties.getSamplingRate()));
        props.setProperty("recordParams", String.valueOf(sqlLogProperties.isRecordParams()));
        props.setProperty("recordFullStack", String.valueOf(sqlLogProperties.isRecordFullStack()));
        props.setProperty("maxStackDepth", String.valueOf(sqlLogProperties.getMaxStackDepth()));
        interceptor.setProperties(props);

        // 注册拦截器到所有 SqlSessionFactory
        for (SqlSessionFactory sqlSessionFactory : sqlSessionFactoryList) {
            sqlSessionFactory.getConfiguration().addInterceptor(interceptor);
        }

        log.info("SQL 日志拦截器已注册: enabled={}, slowQueryThreshold={}ms, samplingRate={}",
                sqlLogProperties.isEnabled(),
                sqlLogProperties.getSlowQueryThreshold(),
                sqlLogProperties.getSamplingRate());

        return new SqlLogAutoConfigurationInitializer();
    }

    /**
     * 配置初始化器
     */
    public static class SqlLogAutoConfigurationInitializer {
        // 占位符，用于触发配置初始化
    }
}
