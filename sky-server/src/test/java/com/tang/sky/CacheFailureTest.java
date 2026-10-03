package com.tang.sky;

import com.tang.sky.config.BestEffortCache;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CacheFailureTest {
    @Test void evictionStillRunsAfterCommitButOutageDoesNotReportCommittedBusinessAsFailed() {
        var redis=mock(Cache.class);when(redis.getName()).thenReturn("dishes");
        doThrow(new IllegalStateException("simulated connection outage")).when(redis).clear();
        var cache=new TransactionAwareCacheDecorator(new BestEffortCache(redis));
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.clear();verify(redis,never()).clear();
            var callbacks=TransactionSynchronizationManager.getSynchronizations();assertEquals(1,callbacks.size());
            assertDoesNotThrow(()->callbacks.forEach(callback->callback.afterCommit()));
            verify(redis,times(1)).clear();
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
