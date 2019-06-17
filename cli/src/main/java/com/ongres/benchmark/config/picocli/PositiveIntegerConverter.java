/*-
 *  § 
 * benchmark: command-line
 *    
 * Copyright (C) 2019 OnGres, Inc.
 *    
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * § §
 */

package com.ongres.benchmark.config.picocli;

import com.google.common.base.Preconditions;

import picocli.CommandLine.ITypeConverter;

public class PositiveIntegerConverter implements ITypeConverter<Integer> {

  @Override
  public Integer convert(String value) throws Exception {
    int positiveValue = Integer.parseInt(value);
    Preconditions.checkArgument(positiveValue >= 0,
        positiveValue + " is not greater or equals to 0");
    return positiveValue;
  }
}
