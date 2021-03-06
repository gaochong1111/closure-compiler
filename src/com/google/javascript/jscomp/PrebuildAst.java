/*
 * Copyright 2017 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A helper class to prebuild ASTs from a list of {@link CompilerInput}. Inputs are parsed into ASTs
 * the first time we try to get them. Get them all now using multiple threads, so they'll be parsed
 * in parallel and already available during the rest of the compilation.
 */
class PrebuildAst {

  private final AbstractCompiler compiler;
  private final int numParallelThreads;

  PrebuildAst(AbstractCompiler compiler, int numParalleThreads) {
    this.compiler = compiler;
    this.numParallelThreads = numParalleThreads;
  }

  void prebuild(List<CompilerInput> inputList) {
    ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(
        numParallelThreads,
        numParallelThreads,
        Integer.MAX_VALUE,
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<Runnable>());
    ListeningExecutorService executorService = MoreExecutors.listeningDecorator(poolExecutor);
    List<ListenableFuture<?>> futureList = new ArrayList<>(inputList.size());
    for (final CompilerInput input : inputList) {
      futureList.add(executorService.submit(new Runnable() {
        @Override
        public void run() {
          input.getAstRoot(compiler);
        }
      }));
    }

    poolExecutor.shutdown();
    try {
      Futures.allAsList(futureList).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

}
