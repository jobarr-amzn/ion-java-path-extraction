/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.amazon.ionpathextraction;

import static com.amazon.ionpathextraction.internal.Preconditions.checkArgument;

import com.amazon.ion.IonReader;
import com.amazon.ionpathextraction.internal.Annotations;
import com.amazon.ionpathextraction.internal.PathExtractorConfig;
import com.amazon.ionpathextraction.pathcomponents.PathComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * {@link PathExtractor} builder.
 */
public final class PathExtractorBuilder<T> {

    private static final boolean DEFAULT_MATCH_RELATIVE_PATHS = false;
    private static final boolean DEFAULT_CASE_INSENSITIVE = false;

    private final List<SearchPath<T>> searchPaths = new ArrayList<>();
    private boolean matchRelativePaths;
    private boolean matchCaseInsensitive;

    private PathExtractorBuilder() {
    }

    /**
     * Creates a new builder with standard configuration.
     *
     * @return new standard builder instance.
     */
    public static <T> PathExtractorBuilder<T> standard() {
        PathExtractorBuilder<T> builder = new PathExtractorBuilder<>();
        builder.matchCaseInsensitive = DEFAULT_CASE_INSENSITIVE;
        builder.matchRelativePaths = DEFAULT_MATCH_RELATIVE_PATHS;

        return builder;
    }

    /**
     * Instantiates a thread safe {@link PathExtractor} configured by this builder.
     *
     * @return new {@link PathExtractor} instance.
     */
    public PathExtractor<T> build() {
        return new PathExtractorImpl<>(searchPaths, new PathExtractorConfig(matchRelativePaths, matchCaseInsensitive));
    }

    /**
     * Sets matchRelativePaths config. When true the path extractor will accept readers at any depth, when false the
     * reader must be at depth zero.
     *
     * <BR>
     * defaults to false.
     *
     * @param matchRelativePaths new config value.
     * @return builder for chaining.
     */
    public PathExtractorBuilder<T> withMatchRelativePaths(final boolean matchRelativePaths) {
        this.matchRelativePaths = matchRelativePaths;

        return this;
    }

    /**
     * Sets matchCaseInsensitive config. When true the path extractor will match fields ignoring case, when false the
     * path extractor will mach respecting the path components case.
     *
     * <BR>
     * defaults to false.
     *
     * @param matchCaseInsensitive new config value.
     * @return builder for chaining.
     */
    public PathExtractorBuilder<T> withMatchCaseInsensitive(final boolean matchCaseInsensitive) {
        this.matchCaseInsensitive = matchCaseInsensitive;

        return this;
    }

    /**
     * Register a callback for a search path.
     *
     * @param searchPathAsIon string representation of a search path.
     * @param callback callback to be registered.
     * @return builder for chaining.
     * @see PathExtractorBuilder#withSearchPath(List, BiFunction, String[])
     */
    public PathExtractorBuilder<T> withSearchPath(final String searchPathAsIon,
                                                  final Function<IonReader, Integer> callback) {
        checkArgument(callback != null, "callback cannot be null");

        withSearchPath(searchPathAsIon, (reader, t) -> callback.apply(reader));

        return this;
    }

    /**
     * Register a callback for a search path.
     *
     * @param searchPathAsIon string representation of a search path.
     * @param callback callback to be registered.
     * @return builder for chaining.
     * @see PathExtractorBuilder#withSearchPath(List, BiFunction, String[])
     */
    public PathExtractorBuilder<T> withSearchPath(final String searchPathAsIon,
                                                  final BiFunction<IonReader, T, Integer> callback) {
        checkArgument(searchPathAsIon != null, "searchPathAsIon cannot be null");
        checkArgument(callback != null, "callback cannot be null");

        SearchPath<T> searchPath = SearchPathParser.parse(searchPathAsIon, callback);
        searchPaths.add(searchPath);

        return this;
    }

    /**
     * Register a callback for a search path.
     *
     * @param pathComponents search path as a list of path components.
     * @param callback callback to be registered.
     * @param annotations annotations used with this search path.
     * @return builder for chaining.
     */
    public PathExtractorBuilder<T> withSearchPath(final List<PathComponent> pathComponents,
                                                  final Function<IonReader, Integer> callback,
                                                  final String[] annotations) {
        checkArgument(callback != null, "callback cannot be null");

        return withSearchPath(pathComponents, (reader, t) -> callback.apply(reader), annotations);
    }

    /**
     * Register a callback for a search path.
     * <p>
     * The callback receives the matcher's {@link IonReader}, positioned on the matching value, so that it can use the
     * appropriate reader method to access the value. The callback return value is a ‘step-out-N’ instruction. The most
     * common value is zero, which tells the extractor to continue with the next value at the same depth. A return value
     * greater than zero may be useful to users who only care about the first match at a particular depth.
     * </p>
     *
     * <p>
     * Callback implementations <strong>MUST</strong> comply with the following:
     * </p>
     *
     * <ul>
     * <li>
     * The reader must not be advanced past the matching value. Violating this will cause the following value to be
     * skipped. If a value is skipped, neither the value itself nor any of its children will be checked for match
     * against any of the extractor's registered paths.
     * </li>
     * <li>
     * If the reader is positioned on a container value, its cursor must be at the same depth when the callback returns.
     * In other words, if the user steps in to the matched value, it must step out an equal number of times. Violating
     * this will raise an error.
     * </li>
     * <li>
     * Return value must be between zero and the the current reader relative depth, for example the following search
     * path (foo bar) must return values between 0 and 2 inclusive.
     * </li>
     * <li>
     * When there are nested search paths, e.g. (foo) and (foo bar), the callback for (foo) should not read the reader
     * value if it's a container. Doing so will advance the reader to the end of the container making impossible to
     * match (foo bar).
     * </li>
     * </ul>
     *
     * @param pathComponents search path as a list of path components.
     * @param callback callback to be registered.
     * @param annotations annotations used with this search path.
     * @return builder for chaining.
     */
    public PathExtractorBuilder<T> withSearchPath(final List<PathComponent> pathComponents,
                                                  final BiFunction<IonReader, T, Integer> callback,
                                                  final String[] annotations) {
        checkArgument(pathComponents != null, "pathComponents cannot be null");
        checkArgument(callback != null, "callback cannot be null");
        checkArgument(annotations != null, "annotations cannot be null");

        searchPaths.add(new SearchPath<>(pathComponents, callback, new Annotations(annotations)));

        return this;
    }
}
