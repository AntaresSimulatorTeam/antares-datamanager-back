package com.rte_france.antares.datamanager_back.configuration.logback;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import co.elastic.logging.logback.EcsEncoder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.io.InputStream;

@Slf4j
public class AntaresJoranConfigurator implements InitializingBean {

    private static final String LOGBACK_FILE_NAME= "logback-spring.xml";

    private static final String JSON_CONSOLE = "JSON_CONSOLE";

    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();


    @Override
    public void afterPropertiesSet() {
        this.configureDefaultLogbackSpring();
    }

    public void  configureDefaultLogbackSpring(){
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);

        try {
            InputStream is = this.getClass().getClassLoader().getResourceAsStream(LOGBACK_FILE_NAME);
            // uncomment this line to remove duplicate logs
           // context.getLoggerList().forEach( e -> e.detachAppender("CONSOLE"));
            configurator.doConfigure(is);
            ConsoleAppender<ILoggingEvent> appender = (ConsoleAppender<ILoggingEvent>) context.getLogger("root").getAppender(JSON_CONSOLE);
          //  EcsEncoder enc = (EcsEncoder) appender.getEncoder();
           // enc.setServiceName("antares-datamanager-back");

        } catch (JoranException je) {
            log.warn(String.format("Error while initializing antares Joran configurator : %s", je.getMessage()));
        }
    }


}
