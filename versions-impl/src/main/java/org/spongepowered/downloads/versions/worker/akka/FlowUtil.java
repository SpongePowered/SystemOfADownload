/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://spongepowered.org/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.downloads.versions.worker.akka;

import akka.Done;
import akka.NotUsed;
import akka.japi.Pair;
import akka.stream.FlowShape;
import akka.stream.UniformFanInShape;
import akka.stream.UniformFanOutShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Merge;
import akka.stream.javadsl.Partition;
import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class FlowUtil {

    @SuppressWarnings("unchecked")
    @SafeVarargs
    public static <T> Flow<T, Done, NotUsed> splitClassFlows(Pair<Class<? extends T>, Flow<? extends T, Done, NotUsed>>... pairs) {
        final List<Pair<Class<? extends T>, Flow<? extends T, Done, NotUsed>>> flowPairs = Arrays.stream(pairs)
            .collect(List.collector());

        Map<Class<? extends T>, Integer> classToIndex = HashMap.ofEntries(IntStream.range(0, flowPairs.size())
            .mapToObj(i -> Tuple.of(pairs[i].first(), i))
            .collect(Collectors.toList()));

        final Function<T, Integer> decider = (message) -> flowPairs.map(Pair::first)
            .filter(clazz -> clazz.isInstance(message))
            .map(classToIndex::get)
            .filter(Option::isDefined)
            .map(Option::get)
            .getOrElse(flowPairs::size);

        final Flow<T, Done, NotUsed> ignored = Flow.fromFunction(message -> Done.done());
        return Flow.fromGraph(GraphDSL.create(builder -> {
            final UniformFanInShape<Done, Done> merge = builder.add(Merge.create(flowPairs.size() + 1));
            final UniformFanOutShape<T, T> fanout = builder
                .add(Partition.create(flowPairs.size() + 1, decider::apply));
            for (int i = 0; i < flowPairs.size(); i++) {
                builder.from(fanout.out(i))
                    .via(builder.add((Flow<T, Done, NotUsed>) flowPairs.get(i).second().async()))
                    .toInlet(merge.in(i));
            }
            builder.from(fanout.out(flowPairs.size()))
                .via(builder.add(ignored))
                .toInlet(merge.in(flowPairs.size()));
            return FlowShape.of(fanout.in(), merge.out());
        }));
    }

    @SuppressWarnings("unchecked")
    public static <T, S extends T> Flow<T, Done, NotUsed> subClassFlow(Flow<S, Done, NotUsed> subFlow) {
        return Flow.<T>create()
            .map(t -> (S) t)
            .via(subFlow);
    }
}
