package com.bc.calvalus.binning;

import java.util.*;

/**
 * Produces spatial bins by processing a given "slice" of observations.
 * A slice is referred to as a spatially contiguous region.
 * The class uses a {@link SpatialBinConsumer} to inform clients about a new slice of spatial bins ready to be consumed.
 *
 * @author Norman Fomferra
 * @see ObservationSlice
 * @see TemporalBinner
 */
public class SpatialBinner {

    private final BinningContext binningContext;
    private final BinningGrid binningGrid;
    private final BinManager binManager;
    private final SpatialBinConsumer consumer;

    // State variables
    private final Map<Long, SpatialBin> activeBinMap;
    private final Map<Long, SpatialBin> finalizedBinMap;
    private final ArrayList<Exception> exceptions;

    /**
     * Constructs a spatial binner.
     *
     * @param binningContext The binning context.
     * @param consumer       The consumer that receives the spatial bins processed from observations.
     */
    public SpatialBinner(BinningContext binningContext, SpatialBinConsumer consumer) {
        this.binningContext = binningContext;
        this.binningGrid = binningContext.getBinningGrid();
        this.binManager = binningContext.getBinManager();
        this.consumer = consumer;
        this.activeBinMap = new HashMap<Long, SpatialBin>();
        this.finalizedBinMap = new HashMap<Long, SpatialBin>();
        this.exceptions = new ArrayList<Exception>();
    }

    /**
     * @return The binning context that will also be passed to {@link  SpatialBinConsumer#consumeSpatialBins(BinningContext, java.util.List)}.
     */
    public BinningContext getBinningContext() {
        return binningContext;
    }

    /**
     * @return The exceptions occured during processing.
     */
    public Exception[] getExceptions() {
        return exceptions.toArray(new Exception[exceptions.size()]);
    }

    /**
     * Processes a slice of observations.
     * Will cause the {@link SpatialBinConsumer} to be invoked.
     *
     * @param observations The observations.
     */
    public void processObservationSlice(Iterable<Observation> observations) {

        finalizedBinMap.putAll(activeBinMap);

        for (Observation observation : observations) {
            Long binIndex = binningGrid.getBinIndex(observation.getLatitude(), observation.getLongitude());
            SpatialBin bin = activeBinMap.get(binIndex);
            if (bin == null) {
                bin = binManager.createSpatialBin(binIndex);
                activeBinMap.put(binIndex, bin);
            }
            binManager.aggregateSpatialBin(observation, bin);
            finalizedBinMap.remove(binIndex);
        }

        if (!finalizedBinMap.isEmpty()) {
            emitSliceBins(finalizedBinMap);
            for (Long key : finalizedBinMap.keySet()) {
                activeBinMap.remove(key);
            }
            finalizedBinMap.clear();
        }
    }

    /**
     * Processes a slice of observations.
     * Convenience method for {@link #processObservationSlice(Iterable)}.
     *
     * @param observations The observations.
     */
    public void processObservationSlice(Observation... observations) {
        processObservationSlice(Arrays.asList(observations));
    }

    /**
     * Must be called after all observations have been send to {@link #processObservationSlice(Iterable)}.
     * Calling this method multiple times has no further effect.
     */
    public void complete() {
        if (!activeBinMap.isEmpty()) {
            emitSliceBins(activeBinMap);
            activeBinMap.clear();
        }
        finalizedBinMap.clear();
    }

    private void emitSliceBins(Map<Long, SpatialBin> binMap) {
        List<SpatialBin> bins = new ArrayList<SpatialBin>(binMap.values());
        for (SpatialBin bin : bins) {
            binManager.completeSpatialBin(bin);
        }
        try {
            consumer.consumeSpatialBins(binningContext, bins);
        } catch (Exception e) {
            exceptions.add(e);
        }
    }
}
