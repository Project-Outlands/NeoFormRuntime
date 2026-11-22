package net.neoforged.neoform.runtime.actions;

import net.neoforged.neoform.runtime.cache.CacheKeyBuilder;
import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.neoform.runtime.graph.ExecutionNodeAction;

import java.io.IOException;

public record ExtractNeoFormDataAction(String dataId) implements ExecutionNodeAction {
    @Override
    public void run(ProcessingEnvironment environment) throws IOException {
        environment.setOutput("output", environment.extractData(dataId));
    }

    @Override
    public void computeCacheKey(CacheKeyBuilder ck) {
        ExecutionNodeAction.super.computeCacheKey(ck);
        ck.addDataSource("extracted file", dataId);
    }
}
