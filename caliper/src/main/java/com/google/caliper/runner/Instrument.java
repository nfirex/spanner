/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.caliper.runner;

import com.google.caliper.api.Benchmark;
import com.google.caliper.util.SimpleDuration;
import com.google.common.collect.ImmutableMap;

import java.lang.reflect.Method;
import java.util.Map;

public abstract class Instrument {
  protected ImmutableMap<String, String> options;

  protected void setOptions(Map<String, String> options) {
    this.options = ImmutableMap.copyOf(options);
  }

  public SimpleDuration estimateRuntimePerTrial() {
    throw new UnsupportedOperationException();
  }

  public abstract boolean isBenchmarkMethod(Method method);

  public abstract BenchmarkMethod createBenchmarkMethod(
      BenchmarkClass benchmarkClass, Method method) throws InvalidBenchmarkException;

  public abstract void dryRun(Benchmark benchmark, BenchmarkMethod method) throws UserCodeException;
}
