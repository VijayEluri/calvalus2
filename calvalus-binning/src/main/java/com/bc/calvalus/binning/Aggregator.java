package com.bc.calvalus.binning;

import java.util.List;

/**
 * An aggregator provides the strategies for spatial and temporal binning. Operating on single bin cells,
 * an aggregator provides the answers for
 * <ul>
 * <li>A. Spatial binning: how are input samples of a single observation (swath) aggregated to spatial bins?</li>
 * <li>B. Temporal binning: how are spatial bins aggregated to temporal bins?</li>
 * <li>C. Final statistics: how are final statistic computed?</li>
 * </ul>
 * <p/>
 * A. Spatial binning: For each bin found in a single observation (swath).
 * <ol>
 * <li>{@link #initSpatial(BinContext, WritableVector)}</li>
 * <li>For each contributing measurement: {@link #aggregateSpatial(BinContext, Vector, WritableVector)}</li>
 * <li>{@link #completeSpatial(BinContext, int, WritableVector)}</li>
 * </ol>
 * <p/>
 * B. Temporal binning: For all bins found in all swaths.
 * <ol>
 * <li>{@link #initTemporal(BinContext, WritableVector)}</li>
 * <li>For each contributing spatial bin: {@link #aggregateTemporal(BinContext, Vector, int, WritableVector)}</li>
 * <li>{@link #completeTemporal(BinContext, int, WritableVector)}</li>
 * </ol>
 * <p/>
 * C. Final statistics: For all bins found in all swaths compute the final statistics.
 * <ol>
 * <li>{@link #computeOutput(Vector, WritableVector)}</li>
 * </ol>
 * <p/>
 * Note for implementors: Aggregators have no state, in order to exchange information within the spatial or temporal
 * binning calling sequences, use the {@link BinContext}.
 *
 * @author Norman Fomferra
 */
public interface Aggregator {

    /**
     * @return The aggregator's name.
     */
    String getName();

    /**
     * @return The array of names of all statistical features used for spatial binning.
     */
    String[] getSpatialFeatureNames();

    /**
     * @return The array of names of all statistical features used for temporal binning.
     */
    String[] getTemporalFeatureNames();

    /**
     * @return The array of names of all statistical features produced as output.
     */
    String[] getOutputFeatureNames();

    /**
     * @return The fill value (no-data value, missing value) used in the output.
     */
    float getOutputFillValue();

    /**
     * Initialises the spatial aggregation vector.
     *
     * @param ctx    The bin context which is shared between calls to {@link #initSpatial},
     *               {@link #aggregateSpatial} and {@link #completeSpatial}.
     * @param vector The aggregation vector to initialise.
     */
    void initSpatial(BinContext ctx, WritableVector vector);

    /**
     * Aggregates a new observation to a spatial aggregation vector.
     *
     * @param ctx               The bin context which is shared between calls to {@link #initSpatial},
     *                          {@link #aggregateSpatial} and {@link #completeSpatial}.
     * @param observationVector The observation.
     * @param spatialVector     The spatial aggregation vector to update.
     */
    void aggregateSpatial(BinContext ctx, Vector observationVector, WritableVector spatialVector);

    /**
     * Informs this aggregation instance that no more measurements will be added to the spatial vector.
     *
     * @param ctx           The bin context which is shared between calls to {@link #initSpatial},
     *                      {@link #aggregateSpatial} and {@link #completeSpatial}.
     * @param numSpatialObs The number of observations added so far.
     * @param spatialVector The spatial aggregation vector to complete.
     */
    void completeSpatial(BinContext ctx, int numSpatialObs, WritableVector spatialVector);

    /**
     * Initialises the temporal aggregation vector.
     *
     * @param ctx    The bin context which is shared between calls to {@link #initTemporal},
     *               {@link #aggregateTemporal} and {@link #completeTemporal}.
     * @param vector The aggregation vector to initialise.
     */
    void initTemporal(BinContext ctx, WritableVector vector);

    /**
     * Aggregates a spatial aggregation to a temporal aggregation vector.
     *
     * @param ctx            The bin context which is shared between calls to {@link #initTemporal},
     *                       {@link #aggregateTemporal} and {@link #completeTemporal}.
     * @param spatialVector  The spatial aggregation.
     * @param numSpatialObs  The number of total observations made in the spatial aggregation.
     * @param temporalVector The temporal aggregation vector to be updated.
     */
    void aggregateTemporal(BinContext ctx, Vector spatialVector, int numSpatialObs, WritableVector temporalVector);

    /**
     * Informs this aggregation instance that no more measurements will be added to the temporal vector.
     *
     * @param ctx            The bin context which is shared between calls to {@link #initTemporal},
     *                       {@link #aggregateTemporal} and {@link #completeTemporal}.
     * @param numTemporalObs The number of observations added so far.
     * @param temporalVector The temporal aggregation vector to complete.
     */
    void completeTemporal(BinContext ctx, int numTemporalObs, WritableVector temporalVector);

    /**
     * Computes the output vector from the temporal vector.
     *
     * @param temporalVector The temporal vector.
     * @param outputVector   The output vector to be computed.
     */
    void computeOutput(Vector temporalVector, WritableVector outputVector);

}
