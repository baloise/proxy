package com.baloise.proxy.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.layout.TTLLLayout;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ConfiguratorRank;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.spi.ContextAwareBase;

@ConfiguratorRank(value = ConfiguratorRank.CUSTOM_TOP_PRIORITY)
public class LogConfigurator extends ContextAwareBase implements Configurator {

    private LoggerContext loggerContext;
	private static LogConfigurator instance;
    
    public LogConfigurator() {
    	if(instance!=null) throw new IllegalStateException("Logging system already initialised");
    	instance = this;
	}
    
    public static LogConfigurator getInstance() {
		if(instance == null) throw new IllegalStateException("Logging system not yet initialised");
    	return instance;
	}

    public void setLevel(Level level) {
    	loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(level);
    }
    
	@Override
    public ExecutionStatus configure(LoggerContext loggerContext) {
        this.loggerContext = loggerContext;
		addInfo("Setting up default configuration.");

        ConsoleAppender<ILoggingEvent> ca = new ConsoleAppender<ILoggingEvent>();
        ca.setContext(context);
        ca.setName("console");
        LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<ILoggingEvent>();
        encoder.setContext(context);

        // same as
        // PatternLayout layout = new PatternLayout();
        // layout.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} -
        // %msg%n");
        TTLLLayout layout = new TTLLLayout();

        layout.setContext(context);
        layout.start();
        encoder.setLayout(layout);

        ca.setEncoder(encoder);
        ca.start();

        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(ca);

        // let the caller decide
        return ExecutionStatus.NEUTRAL;
    }
}
