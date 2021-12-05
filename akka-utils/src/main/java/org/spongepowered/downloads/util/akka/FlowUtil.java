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
package org.spongepowered.downloads.util.akka;

import akka.Done;
import akka.NotUsed;
import akka.japi.Pair;
import akka.stream.FlowShape;
import akka.stream.UniformFanInShape;
import akka.stream.UniformFanOutShape;
import akka.stream.javadsl.Broadcast;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Merge;
import akka.stream.javadsl.Partition;
import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class FlowUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger("FlowUtil");

    @SuppressWarnings("unchecked")
    @SafeVarargs
    public static <T> Flow<T, Done, NotUsed> broadcast(Flow<T, Done, NotUsed>... flows) {
        final var gatheredFlows = Arrays.stream(flows).collect(List.collector());
        final var count = gatheredFlows.size();
        return Flow.fromGraph(GraphDSL.create(builder -> {
            final var broadcast = builder.add(Broadcast.<T>create(count));
            final var merge = builder.add(Merge.<Done>create(count));
            for (int i = 0; i < count; i++) {
                builder.from(broadcast.out(i))
                    .via(builder.add(gatheredFlows.get(i)))
                    .toInlet(merge.in(i));
            }
            return FlowShape.apply(broadcast.in(), merge.out());
        }));
    }

    @SuppressWarnings("unchecked")
    @SafeVarargs
    public static <T> Flow<T, Done, NotUsed> splitClassFlows(
        Pair<Class<? extends T>, Flow<? extends T, Done, NotUsed>>... pairs
    ) {
        final List<Pair<Class<? extends T>, Flow<? extends T, Done, NotUsed>>> flowPairs = Arrays.stream(pairs)
            .collect(List.collector());
        final var count = flowPairs.size();

        final Map<Class<? extends T>, Integer> classToIndex = HashMap.ofEntries(
            IntStream.range(0, count)
                .mapToObj(i -> Tuple.of(pairs[i].first(), i))
                .collect(Collectors.toList())
        );

        final Function<T, Integer> decider = (message) -> flowPairs.map(Pair::first)
            .filter(clazz -> clazz.isInstance(message))
            .map(classToIndex::get)
            .filter(Option::isDefined)
            .map(Option::get)
            .getOrElse(count);

        final Flow<T, Done, NotUsed> ignored = Flow.fromFunction(message -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("ignoring message {}", message);
            }
            return Done.done();
        });
        return Flow.fromGraph(GraphDSL.create(builder -> {
            final UniformFanInShape<Done, Done> merge = builder.add(Merge.create(count + 1));
            final UniformFanOutShape<T, T> fanout = builder
                .add(Partition.create(count + 1, decider::apply));
            for (int i = 0; i < count; i++) {
                builder.from(fanout.out(i))
                    .via(builder.add((Flow<T, Done, NotUsed>) flowPairs.get(i).second().async()))
                    .toInlet(merge.in(i));
            }
            builder.from(fanout.out(count))
                .via(builder.add(ignored))
                .toInlet(merge.in(count));
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
