package top.tasaed.aohx.monohydrate.profiler.mixins;

import age.of.civilizations2.jakowski.lukasz.CFG;
import age.of.civilizations2.jakowski.lukasz.City;
import age.of.civilizations2.jakowski.lukasz.Files.FileManager;
import age.of.civilizations2.jakowski.lukasz.Province;
import age.of.civilizations2.jakowski.lukasz.Province_GameData2;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.IOException;
import java.util.List;
import team.rainfall.finality.FinalityLogger;
import team.rainfall.finality.luminosity2.CallbackInfo;
import team.rainfall.finality.luminosity2.annotations.Inject;
import team.rainfall.finality.luminosity2.annotations.Mixin;
import team.rainfall.finality.luminosity2.annotations.Overwrite;
import team.rainfall.finality.luminosity2.annotations.Shadow;
import team.rainfall.finality.luminosity2.utils.InjectPosition;
import top.tasaed.aohx.monohydrate.core.MonohydrateConfig;
import top.tasaed.aohx.monohydrate.load.ProvinceFileCache;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.Stage;

@Mixin(mixinClass = "age.of.civilizations2.jakowski.lukasz.Core.Core")
public abstract class MixinCoreProvinceProfile {
    // NewFieldProcessor reuses the existing field by name and descriptor.
    @Shadow
    private List<City> lCities;

    @Shadow
    private List<Province> lProvs;

    @Shadow
    public abstract void build_LoadProvince(int id);

    @Shadow
    public abstract Province getProv(int id);

    @Shadow
    public abstract boolean ptCS(int provinceId, int x, int y);

    @Inject(methodName = "build_LoadProvince", position = InjectPosition.HEAD)
    public void monohydrate$invalidateProvinceCandidates(int id, CallbackInfo callback) {
        ProvinceFileCache.invalidateData();
    }

    @Inject(methodName = "build_LoadProvince", position = InjectPosition.RETURN)
    public void monohydrate$rebuiltProvinceCandidates(int id, CallbackInfo callback) {
        ProvinceFileCache.invalidateData();
    }

    @Overwrite
    public final void buildCitiesProvinceID() {
        LoadingProfiler.beginStage(Stage.CITIES_ASSIGN);
        int cityCount = 0;
        long candidateProvinceChecks = 0;
        long aabbPasses = 0;
        long ptCSCalls = 0;
        try {
            cityCount = lCities.size();
            if (!MonohydrateConfig.CITY_OPTIMIZATIONS_ENABLED) {
                // Preserve the original game's scan, including repeated coordinate and province reads.
                for (int i = cityCount - 1; i >= 0; i--) {
                    for (int j = 0; j < CFG.core.getProvinSize(); j++) {
                        if (MonohydrateConfig.PROFILER_ENABLED) {
                            candidateProvinceChecks++;
                        }
                        if (this.getProv(j).getMiX2()
                                        <= lCities.get(i).getPoX()
                                                * CFG.map.getMpB().getMapSc3()
                                && this.getProv(j).getMaX7()
                                        >= lCities.get(i).getPoX()
                                                * CFG.map.getMpB().getMapSc3()
                                && this.getProv(j).getMiY4()
                                        <= lCities.get(i).getPosY()
                                                * CFG.map.getMpB().getMapSc3()
                                && this.getProv(j).getMaY6()
                                        >= lCities.get(i).getPosY()
                                                * CFG.map.getMpB().getMapSc3()) {
                            if (MonohydrateConfig.PROFILER_ENABLED) {
                                aabbPasses++;
                            }
                            int x = lCities.get(i).getPoX() * CFG.map.getMpB().getMapSc3();
                            int y = lCities.get(i).getPosY() * CFG.map.getMpB().getMapSc3();
                            if (MonohydrateConfig.PROFILER_ENABLED) {
                                ptCSCalls++;
                            }
                            if (this.ptCS(j, x, y)) {
                                this.getProv(j).addCity(lCities.get(i));
                                break;
                            }
                        }
                    }
                }
            } else if (cityCount > 0 && CFG.core.getProvinSize() > 0) {
                int provinceCount = CFG.core.getProvinSize();
                int minX = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                int minY = Integer.MAX_VALUE;
                int maxY = Integer.MIN_VALUE;
                for (int j = 0; j < provinceCount; j++) {
                    Province province = this.getProv(j);
                    minX = Math.min(minX, province.getMiX2());
                    maxX = Math.max(maxX, province.getMaX7());
                    minY = Math.min(minY, province.getMiY4());
                    maxY = Math.max(maxY, province.getMaY6());
                }
                // Bound the table size independently of map dimensions; widen before subtracting.
                int gridSide = 32;
                long cellWidth = Math.max(1L, ((long) maxX - minX) / gridSide + 1);
                long cellHeight = Math.max(1L, ((long) maxY - minY) / gridSide + 1);
                int[][] candidates = new int[gridSide * gridSide][];
                int[] sizes = new int[candidates.length];
                // Count first, then fill exact-sized primitive arrays in ascending province ID order.
                for (int pass = 0; pass < 2; pass++) {
                    for (int j = 0; j < provinceCount; j++) {
                        Province province = this.getProv(j);
                        int left = province.getMiX2();
                        int right = province.getMaX7();
                        int top = province.getMiY4();
                        int bottom = province.getMaY6();
                        if (left > right || top > bottom) {
                            continue;
                        }
                        int firstColumn = (int) (((long) left - minX) / cellWidth);
                        int lastColumn = (int) (((long) right - minX) / cellWidth);
                        int firstRow = (int) (((long) top - minY) / cellHeight);
                        int lastRow = (int) (((long) bottom - minY) / cellHeight);
                        // Include the maximum edge's cell: the original AABB includes both edges.
                        for (int row = firstRow; row <= lastRow; row++) {
                            for (int column = firstColumn; column <= lastColumn; column++) {
                                int cell = row * gridSide + column;
                                if (pass == 0) {
                                    sizes[cell]++;
                                } else {
                                    candidates[cell][sizes[cell]++] = j;
                                }
                            }
                        }
                    }
                    if (pass == 0) {
                        for (int cell = 0; cell < candidates.length; cell++) {
                            if (sizes[cell] > 0) {
                                candidates[cell] = new int[sizes[cell]];
                                sizes[cell] = 0;
                            }
                        }
                    }
                }
                for (int i = cityCount - 1; i >= 0; i--) {
                    City city = lCities.get(i);
                    int x = city.getPoX() * CFG.map.getMpB().getMapSc3();
                    int y = city.getPosY() * CFG.map.getMpB().getMapSc3();
                    if (x < minX || x > maxX || y < minY || y > maxY) {
                        continue;
                    }
                    int column = (int) (((long) x - minX) / cellWidth);
                    int row = (int) (((long) y - minY) / cellHeight);
                    int[] cityCandidates = candidates[row * gridSide + column];
                    if (cityCandidates == null) {
                        continue;
                    }
                    for (int j : cityCandidates) {
                        if (MonohydrateConfig.PROFILER_ENABLED) {
                            candidateProvinceChecks++;
                        }
                        Province province = this.getProv(j);
                        if (province.getMiX2() <= x
                                && province.getMaX7() >= x
                                && province.getMiY4() <= y
                                && province.getMaY6() >= y) {
                            if (MonohydrateConfig.PROFILER_ENABLED) {
                                aabbPasses++;
                                ptCSCalls++;
                            }
                            if (this.ptCS(j, x, y)) {
                                province.addCity(city);
                                break;
                            }
                        }
                    }
                }
            }
            lCities.clear();
            lCities = null;
        } finally {
            // Preserve partial work on failure without recording a successful duration sample.
            LoadingProfiler.recordCitiesAssignment(cityCount, candidateProvinceChecks, aabbPasses, ptCSCalls);
        }
        LoadingProfiler.endStage(Stage.CITIES_ASSIGN);
    }

    // Luminosity 1.6.0 reprocesses all existing @Inject methods after each Core mixin.
    // Keep these stage calls in the method body so later mixins cannot inject them twice.
    @Overwrite
    public final void loadProvince(int id) {
        String path = "map/" + CFG.map.getFileActiveMapPath() + "data/provinces/" + id;
        LoadingProfiler.beginPathStage(Stage.PROVINCE_PATH, id);
        FileHandle file = FileManager.loadFile(path);
        LoadingProfiler.endStage(Stage.PROVINCE_PATH);

        try {
            LoadingProfiler.beginStage(Stage.PROVINCE_READ);
            byte[] bytes = file.readBytes();
            LoadingProfiler.endStage(Stage.PROVINCE_READ);
            LoadingProfiler.beginStage(Stage.PROVINCE_DESERIALIZE);
            Object data = CFG.deserialize(bytes);
            LoadingProfiler.endStage(Stage.PROVINCE_DESERIALIZE);
            LoadingProfiler.beginStage(Stage.PROVINCE_CONSTRUCT);
            Province province = new Province(id, (Province_GameData2) data);
            LoadingProfiler.endStage(Stage.PROVINCE_CONSTRUCT);
            lProvs.add(province);
        } catch (ClassNotFoundException e) {
            if (CFG.LOGs) {
                FinalityLogger.error("[Monohydrate] Failed to deserialize province " + id, e);
            }
        } catch (IOException | GdxRuntimeException e) {
            // A failed stage stays unreturned; do not turn it into a successful sample in finally.
            LoadingProfiler.beginStage(Stage.PROVINCE_REBUILD);
            this.build_LoadProvince(id);
            LoadingProfiler.endStage(Stage.PROVINCE_REBUILD);
            if (CFG.LOGs) {
                FinalityLogger.error("[Monohydrate] Rebuilt province " + id + " after loading failed", e);
            }
        }
    }
}
