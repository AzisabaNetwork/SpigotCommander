package net.azisaba.spigotcommander.util;

import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Thread local instance of LoggedBufferedOutputStream. Internal buffer is wrapped with ThreadLocal.
 */
public class ThreadLocalLoggedBufferedOutputStream extends SimpleLoggedBufferedOutputStream {
    private final ThreadLocal<StringBuffer> buf = ThreadLocal.withInitial(StringBuffer::new);

    public ThreadLocalLoggedBufferedOutputStream(@NotNull String name, @NotNull Level level) {
        super(LoggerFactory.getLogger(name), name, level);
    }

    @Override
    protected @NotNull String getBuffer() {
        return this.buf.get().toString();
    }

    @Override
    protected void appendBuffer(@NotNull Object o) {
        this.buf.get().append(o);
    }

    @Override
    protected void setBuffer(@NotNull String buf) {
        this.buf.set(new StringBuffer(buf));
    }
}
