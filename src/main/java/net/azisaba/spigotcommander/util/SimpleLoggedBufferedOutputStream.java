package net.azisaba.spigotcommander.util;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Simple instance of LoggedBufferedOutputStream without any protection against concurrent modification.
 */
public class SimpleLoggedBufferedOutputStream extends LoggedBufferedOutputStream {
    protected final Logger logger;
    protected final String name;
    protected final Level level;
    private StringBuffer buf = new StringBuffer();

    public SimpleLoggedBufferedOutputStream(@NotNull String name, @NotNull Level level) {
        this(LoggerFactory.getLogger(name), name, level);
    }

    protected SimpleLoggedBufferedOutputStream(@NotNull Logger logger, @NotNull String name, @NotNull Level level) {
        this.logger = logger;
        this.name = name;
        this.level = level;
    }

    @Override
    protected @NotNull String getBuffer() {
        return this.buf.toString();
    }

    @Override
    protected void appendBuffer(@NotNull Object o) {
        buf.append(o);
    }

    @Override
    protected void setBuffer(@NotNull String buf) {
        this.buf = new StringBuffer(buf);
    }

    @Override
    protected void log(@NotNull String buf) {
        if (level == Level.INFO) {
//            logger.info("[{}]: {}", name, buf);
            logger.info(buf);
        } else if (level == Level.WARN) {
//            logger.warn("[{}]: {}", name, buf);
            logger.warn(buf);
        } else if (level == Level.ERROR) {
//            logger.error("[{}]: {}", name, buf);
            logger.error(buf);
        }
    }
}
