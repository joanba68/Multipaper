package puregero.multipaper.server.velocity.migration.strategy;

import puregero.multipaper.server.velocity.BaseStrategy;

import java.util.concurrent.TimeUnit;

public class None extends BaseStrategy {
    public None(Long interval, TimeUnit timeUnit) {
        super(Long.MAX_VALUE, TimeUnit.DAYS);
    }
}
