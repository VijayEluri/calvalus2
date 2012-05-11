package org.esa.beam.binning.operator;

import com.vividsolutions.jts.geom.Geometry;
import org.esa.beam.binning.Aggregator;
import org.esa.beam.binning.BinningContext;
import org.esa.beam.binning.PlanetaryGrid;
import org.esa.beam.binning.TemporalBin;
import org.esa.beam.binning.support.SEAGrid;
import org.esa.beam.binning.support.SeadasGrid;
import org.esa.beam.framework.datamodel.ProductData;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriteable;
import ucar.nc2.Variable;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Utility class that writes SeaDAS-Level-3-alike NetCDF files containing binned Level-3 data.
 *
 * @author Norman Fomferra
 */
public class BinWriter {

    final Logger logger;

    public BinWriter(Logger logger) {
        this.logger = logger;
    }

    public void write(File filePath,
                      List<TemporalBin> temporalBins,
                      BinningContext binningContext,
                      Geometry region,
                      ProductData.UTC startTime,
                      ProductData.UTC stopTime) throws IOException, InvalidRangeException {

        final NetcdfFileWriteable netcdfFile = NetcdfFileWriteable.createNew(filePath.getPath());

        final PlanetaryGrid planetaryGrid = binningContext.getPlanetaryGrid();
        netcdfFile.addGlobalAttribute("title", "Level-3 Binned Data");
        netcdfFile.addGlobalAttribute("super_sampling", binningContext.getSuperSampling());
        if (region != null) {
            netcdfFile.addGlobalAttribute("region", region.toText());
        }
        DateFormat dateFormat = ProductData.UTC.createDateFormat("yyyy-MM-dd");
        netcdfFile.addGlobalAttribute("start_time", startTime != null ? dateFormat.format(startTime.getAsDate()) : "");
        netcdfFile.addGlobalAttribute("stop_time", stopTime != null ? dateFormat.format(stopTime.getAsDate()) : "");

        netcdfFile.addGlobalAttribute("SEAGrid_bins", 2 * planetaryGrid.getNumRows());
        netcdfFile.addGlobalAttribute("SEAGrid_radius", SEAGrid.RE);
        netcdfFile.addGlobalAttribute("SEAGrid_max_north", +90.0);
        netcdfFile.addGlobalAttribute("SEAGrid_max_south", -90.0);
        netcdfFile.addGlobalAttribute("SEAGrid_seam_lon", -180.0);

        final Dimension binIndexDim = netcdfFile.addDimension("bin_index", planetaryGrid.getNumRows());
        final Dimension binListDim = netcdfFile.addDimension("bin_list", temporalBins.size());

        final Variable rowNumVar = netcdfFile.addVariable("bi_row_num", DataType.INT, new Dimension[]{binIndexDim});
        final Variable vsizeVar = netcdfFile.addVariable("bi_vsize", DataType.DOUBLE, new Dimension[]{binIndexDim});
        final Variable hsizeVar = netcdfFile.addVariable("bi_hsize", DataType.DOUBLE, new Dimension[]{binIndexDim});
        final Variable startNumVar = netcdfFile.addVariable("bi_start_num", DataType.INT, new Dimension[]{binIndexDim});
        final Variable maxVar = netcdfFile.addVariable("bi_max", DataType.INT, new Dimension[]{binIndexDim});

        final Variable binNumVar = netcdfFile.addVariable("bl_bin_num", DataType.INT, new Dimension[]{binListDim});
        final Variable numObsVar = netcdfFile.addVariable("bl_nobs", DataType.INT, new Dimension[]{binListDim});
        final Variable numScenesVar = netcdfFile.addVariable("bl_nscenes", DataType.INT, new Dimension[]{binListDim});
        final int aggregatorCount = binningContext.getBinManager().getAggregatorCount();
        final ArrayList<Variable> featureVars = new ArrayList<Variable>(3 * aggregatorCount);
        for (int i = 0; i < aggregatorCount; i++) {
            final Aggregator aggregator = binningContext.getBinManager().getAggregator(i);
            final String[] featureNames = aggregator.getTemporalFeatureNames();
            for (String featureName : featureNames) {
                final Variable featureVar = netcdfFile.addVariable("bl_" + featureName, DataType.FLOAT, new Dimension[]{binListDim});
                featureVar.addAttribute(new Attribute("_FillValue", aggregator.getOutputFillValue()));
                featureVars.add(featureVar);
            }
        }

        netcdfFile.create();
        final SeadasGrid seadasGrid = new SeadasGrid(planetaryGrid);
        writeBinIndexVariables(netcdfFile, rowNumVar, vsizeVar, hsizeVar, startNumVar, maxVar, seadasGrid);
        writeBinListVariables(netcdfFile, binNumVar, numObsVar, numScenesVar, featureVars, seadasGrid, temporalBins);
        netcdfFile.close();
    }

    private void writeBinIndexVariables(NetcdfFileWriteable netcdfFile, Variable rowNumVar, Variable vsizeVar, Variable hsizeVar, Variable startNumVar, Variable maxVar, SeadasGrid grid) throws IOException, InvalidRangeException {
        writeBinIndexVariable(netcdfFile, rowNumVar, grid, new BinIndexElementSetter() {
            @Override
            public void setArray(Array array, int rowIndex, SeadasGrid grid) {
                array.setInt(rowIndex, grid.convertRowIndex(rowIndex));
            }
        });
        writeBinIndexVariable(netcdfFile, startNumVar, grid, new BinIndexElementSetter() {
            @Override
            public void setArray(Array array, int rowIndex, SeadasGrid grid) {
                array.setInt(rowIndex, grid.getBinIndex(rowIndex));
            }
        });
        writeBinIndexVariable(netcdfFile, vsizeVar, grid, new BinIndexElementSetter() {
            @Override
            public void setArray(Array array, int rowIndex, SeadasGrid grid) {
                array.setDouble(rowIndex, 180.0 / grid.getNumRows());
            }
        });
        writeBinIndexVariable(netcdfFile, hsizeVar, grid, new BinIndexElementSetter() {
            @Override
            public void setArray(Array array, int rowIndex, SeadasGrid grid) {
                array.setDouble(rowIndex, 360.0 / grid.getNumCols(rowIndex));
            }
        });
        writeBinIndexVariable(netcdfFile, maxVar, grid, new BinIndexElementSetter() {
            @Override
            public void setArray(Array array, int rowIndex, SeadasGrid grid) {
                array.setInt(rowIndex, grid.getNumCols(rowIndex));
            }
        });
    }

    private void writeBinListVariables(NetcdfFileWriteable netcdfFile, Variable binNumVar, Variable numObsVar, Variable numScenesVar, List<Variable> featureVars, final SeadasGrid seadasGrid, List<TemporalBin> temporalBins) throws IOException, InvalidRangeException {
        writeBinListVariable(netcdfFile, binNumVar, temporalBins, new BinListElementSetter() {
            @Override
            public void setArray(Array array, int binIndex, TemporalBin bin) {
                array.setInt(binIndex, seadasGrid.getBinIndex(bin.getIndex()));
            }
        });
        writeBinListVariable(netcdfFile, numObsVar, temporalBins, new BinListElementSetter() {
            @Override
            public void setArray(Array array, int binIndex, TemporalBin bin) {
                array.setInt(binIndex, bin.getNumObs());
            }
        });
        writeBinListVariable(netcdfFile, numScenesVar, temporalBins, new BinListElementSetter() {
            @Override
            public void setArray(Array array, int binIndex, TemporalBin bin) {
                array.setInt(binIndex, bin.getNumPasses());
            }
        });

        for (int featureIndex = 0; featureIndex < featureVars.size(); featureIndex++) {
            final int k = featureIndex;
            writeBinListVariable(netcdfFile, featureVars.get(k), temporalBins, new BinListElementSetter() {
                @Override
                public void setArray(Array array, int binIndex, TemporalBin bin) {
                    array.setFloat(binIndex, bin.getFeatureValues()[k]);
                }
            });
        }
    }

    private void writeBinIndexVariable(NetcdfFileWriteable netcdfFile, Variable variable, SeadasGrid seadasGrid, BinIndexElementSetter setter) throws IOException, InvalidRangeException {
        logger.info("Writing bin index variable " + variable.getName());
        final int numRows = seadasGrid.getNumRows();
        final Array array = Array.factory(variable.getDataType(), new int[]{numRows});
        for (int row = 0; row < numRows; row++) {
            setter.setArray(array, row, seadasGrid);
        }
        netcdfFile.write(variable.getName(), array);
    }

    private void writeBinListVariable(NetcdfFileWriteable netcdfFile, Variable variable, List<TemporalBin> temporalBins, BinListElementSetter setter) throws IOException, InvalidRangeException {
        logger.info("Writing bin list variable " + variable.getName());

        final int BUFFER_SIZE = 4096;
        final int[] origin = new int[1];
        final Array buffer = Array.factory(variable.getDataType(), new int[]{BUFFER_SIZE});
        int bufferIndex = 0;
        final String varName = variable.getName();
        for (TemporalBin temporalBin : temporalBins) {
            if (bufferIndex == BUFFER_SIZE) {
                netcdfFile.write(varName, origin, buffer);
                bufferIndex = 0;
                origin[0] += BUFFER_SIZE;
            }
            setter.setArray(buffer, bufferIndex, temporalBin);
            bufferIndex++;
        }
        if (bufferIndex > 0) {
            netcdfFile.write(varName, origin, buffer.section(new int[]{0}, new int[]{bufferIndex}));
        }
    }

    private interface BinIndexElementSetter {
        void setArray(Array array, int rowIndex, SeadasGrid grid);
    }

    private interface BinListElementSetter {
        void setArray(Array array, int binIndex, TemporalBin bin);
    }


}
