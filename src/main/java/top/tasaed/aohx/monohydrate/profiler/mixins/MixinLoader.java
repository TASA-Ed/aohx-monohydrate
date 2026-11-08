package top.tasaed.aohx.monohydrate.profiler.mixins;

import team.rainfall.finality.luminosity2.CallbackInfo;
import team.rainfall.finality.luminosity2.annotations.Inject;
import team.rainfall.finality.luminosity2.annotations.Mixin;
import team.rainfall.finality.luminosity2.utils.InjectPosition;
import top.tasaed.aohx.monohydrate.core.MonohydrateConfig;
import top.tasaed.aohx.monohydrate.load.ProvinceFileCache;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.Point;

@Mixin(mixinClass = "age.of.civilizations2.jakowski.lukasz.Menus.Menu_InitGame")
public class MixinLoader {
    // Luminosity copies fields but not constructor initializers: initialize at the first HEAD.
    private LoadingProfiler monohydrate$profiler;
    private boolean monohydrate$candidatesComplete;

    @Inject(methodName = "loadAssets", position = InjectPosition.HEAD)
    public void monohydrate$beginLoading(CallbackInfo callback) {
        if (!monohydrate$candidatesComplete) ProvinceFileCache.beginFrame(this);
        if (!MonohydrateConfig.PROFILER_ENABLED) {
            return;
        }
        if (monohydrate$profiler == null) {
            monohydrate$profiler = new LoadingProfiler();
        }
        monohydrate$profiler.beginFrame();
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.RETURN)
    public void monohydrate$endLoading(CallbackInfo callback) {
        ProvinceFileCache.endFrame(this);
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.endFrame();
        }
    }

    // Both the language-selection and main-menu branches signal completion.
    // Export is deferred until RETURN so the final frame and shader update are included.
    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "setMenuID")
    public void monohydrate$languageMenuReady(CallbackInfo callback) {
        monohydrate$candidatesComplete = true;
        ProvinceFileCache.endSession(this);
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.markComplete();
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "setMenuIDWithoutAnim")
    public void monohydrate$mainMenuReady(CallbackInfo callback) {
        monohydrate$candidatesComplete = true;
        ProvinceFileCache.endSession(this);
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.markComplete();
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadColors")
    public void monohydrate$begin$loadColors(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_COLORS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadColors")
    public void monohydrate$end$loadColors(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_COLORS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadGameMap")
    public void monohydrate$begin$loadGameMap(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_GAME_MAP);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadGameMap")
    public void monohydrate$end$loadGameMap(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_GAME_MAP);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadBackground")
    public void monohydrate$begin$loadBackground(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_BACKGROUND);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadBackground")
    public void monohydrate$end$loadBackground(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_BACKGROUND);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "init")
    public void monohydrate$begin$init(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.INIT);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "init")
    public void monohydrate$end$init(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.INIT);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "init2")
    public void monohydrate$begin$init2(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.INIT2);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "init2")
    public void monohydrate$end$init2(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.INIT2);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadReligions")
    public void monohydrate$begin$loadReligions(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_RELIGIONS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadReligions")
    public void monohydrate$end$loadReligions(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_RELIGIONS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "initMapContinents")
    public void monohydrate$begin$initMapContinents(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.INIT_MAP_CONTINENTS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "initMapContinents")
    public void monohydrate$end$initMapContinents(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.INIT_MAP_CONTINENTS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "initMapRegions")
    public void monohydrate$begin$initMapRegions(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.INIT_MAP_REGIONS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "initMapRegions")
    public void monohydrate$end$initMapRegions(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.INIT_MAP_REGIONS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadFontArmy")
    public void monohydrate$begin$loadFontArmy(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_FONT_ARMY);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadFontArmy")
    public void monohydrate$end$loadFontArmy(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_FONT_ARMY);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "updateCivPersonalityType")
    public void monohydrate$begin$updateCivPersonalityType(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.UPDATE_CIV_PERSONALITY_TYPE);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "updateCivPersonalityType")
    public void monohydrate$end$updateCivPersonalityType(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.UPDATE_CIV_PERSONALITY_TYPE);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadMapOverlays")
    public void monohydrate$begin$loadMapOverlays(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_MAP_OVERLAYS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadMapOverlays")
    public void monohydrate$end$loadMapOverlays(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_MAP_OVERLAYS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "lO")
    public void monohydrate$begin$lO(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.L_O);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "lO")
    public void monohydrate$end$lO(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.L_O);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "lOI")
    public void monohydrate$begin$lOI(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.L_OI);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "lOI")
    public void monohydrate$end$lOI(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.L_OI);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "lOI2")
    public void monohydrate$begin$lOI2(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.L_OI2);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "lOI2")
    public void monohydrate$end$lOI2(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.L_OI2);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "addIMG")
    public void monohydrate$begin$addIMG(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.ADD_IMG);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "addIMG")
    public void monohydrate$end$addIMG(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.ADD_IMG);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadArmyBGImages")
    public void monohydrate$begin$loadArmyBGImages(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_ARMY_BGIMAGES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadArmyBGImages")
    public void monohydrate$end$loadArmyBGImages(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_ARMY_BGIMAGES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadSparks")
    public void monohydrate$begin$loadSparks(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SPARKS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadSparks")
    public void monohydrate$end$loadSparks(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SPARKS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadIdeologies")
    public void monohydrate$begin$loadIdeologies(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_IDEOLOGIES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadIdeologies")
    public void monohydrate$end$loadIdeologies(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_IDEOLOGIES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "uDPN")
    public void monohydrate$begin$uDPN(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.U_DPN);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "uDPN")
    public void monohydrate$end$uDPN(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.U_DPN);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadShipLines")
    public void monohydrate$begin$loadShipLines(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SHIP_LINES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadShipLines")
    public void monohydrate$end$loadShipLines(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SHIP_LINES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "readSettings")
    public void monohydrate$begin$readSettings(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.READ_SETTINGS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "readSettings")
    public void monohydrate$end$readSettings(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.READ_SETTINGS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loCI")
    public void monohydrate$begin$loCI(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LO_CI);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loCI")
    public void monohydrate$end$loCI(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LO_CI);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "updateCloudsInterface")
    public void monohydrate$begin$updateCloudsInterface(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.UPDATE_CLOUDS_INTERFACE);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "updateCloudsInterface")
    public void monohydrate$end$updateCloudsInterface(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.UPDATE_CLOUDS_INTERFACE);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadProvince")
    public void monohydrate$begin$loadProvince(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_PROVINCE);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadProvince")
    public void monohydrate$end$loadProvince(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_PROVINCE);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadProvinceTexture")
    public void monohydrate$begin$loadProvinceTexture(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_PROVINCE_TEXTURE);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadProvinceTexture")
    public void monohydrate$end$loadProvinceTexture(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_PROVINCE_TEXTURE);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "initGameScenarios")
    public void monohydrate$begin$initGameScenarios(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.INIT_GAME_SCENARIOS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "initGameScenarios")
    public void monohydrate$end$initGameScenarios(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.INIT_GAME_SCENARIOS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadChallenges")
    public void monohydrate$begin$loadChallenges(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_CHALLENGES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadChallenges")
    public void monohydrate$end$loadChallenges(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_CHALLENGES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "updateProvincesSize")
    public void monohydrate$begin$updateProvincesSize(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.UPDATE_PROVINCES_SIZE);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "updateProvincesSize")
    public void monohydrate$end$updateProvincesSize(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.UPDATE_PROVINCES_SIZE);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "updateDrawProvinces")
    public void monohydrate$begin$updateDrawProvinces(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.UPDATE_DRAW_PROVINCES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "updateDrawProvinces")
    public void monohydrate$end$updateDrawProvinces(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.UPDATE_DRAW_PROVINCES);
        }
    }

    @Inject(
            methodName = "loadAssets",
            position = InjectPosition.BEFORE_INVOKE,
            locator = "checkLandBySeaProvincesBorders")
    public void monohydrate$begin$checkLandBySeaProvincesBorders(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.CHECK_LAND_BY_SEA_PROVINCES_BORDERS);
        }
    }

    @Inject(
            methodName = "loadAssets",
            position = InjectPosition.AFTER_INVOKE,
            locator = "checkLandBySeaProvincesBorders")
    public void monohydrate$end$checkLandBySeaProvincesBorders(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.CHECK_LAND_BY_SEA_PROVINCES_BORDERS);
        }
    }

    @Inject(
            methodName = "loadAssets",
            position = InjectPosition.BEFORE_INVOKE,
            locator = "checkSeaBySeaProvincesBorders")
    public void monohydrate$begin$checkSeaBySeaProvincesBorders(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.CHECK_SEA_BY_SEA_PROVINCES_BORDERS);
        }
    }

    @Inject(
            methodName = "loadAssets",
            position = InjectPosition.AFTER_INVOKE,
            locator = "checkSeaBySeaProvincesBorders")
    public void monohydrate$end$checkSeaBySeaProvincesBorders(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.CHECK_SEA_BY_SEA_PROVINCES_BORDERS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "buildProvinceBorder")
    public void monohydrate$begin$buildProvinceBorder(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.BUILD_PROVINCE_BORDER);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "buildProvinceBorder")
    public void monohydrate$end$buildProvinceBorder(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.BUILD_PROVINCE_BORDER);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadFontBorder")
    public void monohydrate$begin$loadFontBorder(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_FONT_BORDER);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadFontBorder")
    public void monohydrate$end$loadFontBorder(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_FONT_BORDER);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "initGameCities")
    public void monohydrate$begin$initGameCities(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.INIT_GAME_CITIES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "initGameCities")
    public void monohydrate$end$initGameCities(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.INIT_GAME_CITIES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadCities")
    public void monohydrate$begin$loadCities(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_CITIES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadCities")
    public void monohydrate$end$loadCities(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_CITIES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "buildProvincesNames")
    public void monohydrate$begin$buildProvincesNames(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.BUILD_PROVINCES_NAMES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "buildProvincesNames")
    public void monohydrate$end$buildProvincesNames(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.BUILD_PROVINCES_NAMES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadProvinceNamesPoints")
    public void monohydrate$begin$loadProvinceNamesPoints(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_PROVINCE_NAMES_POINTS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadProvinceNamesPoints")
    public void monohydrate$end$loadProvinceNamesPoints(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_PROVINCE_NAMES_POINTS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "addSimpleTask")
    public void monohydrate$begin$addSimpleTask(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.ADD_SIMPLE_TASK);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "addSimpleTask")
    public void monohydrate$end$addSimpleTask(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.ADD_SIMPLE_TASK);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadScenario_1")
    public void monohydrate$begin$loadScenario_1(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SCENARIO_1);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadScenario_1")
    public void monohydrate$end$loadScenario_1(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SCENARIO_1);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadScenario_2")
    public void monohydrate$begin$loadScenario_2(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SCENARIO_2);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadScenario_2")
    public void monohydrate$end$loadScenario_2(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SCENARIO_2);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadScenario_2_Flags")
    public void monohydrate$begin$loadScenario_2_Flags(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SCENARIO_2_FLAGS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadScenario_2_Flags")
    public void monohydrate$end$loadScenario_2_Flags(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SCENARIO_2_FLAGS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadScenario_3")
    public void monohydrate$begin$loadScenario_3(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SCENARIO_3);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadScenario_3")
    public void monohydrate$end$loadScenario_3(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SCENARIO_3);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadScenario_4")
    public void monohydrate$begin$loadScenario_4(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SCENARIO_4);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadScenario_4")
    public void monohydrate$end$loadScenario_4(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SCENARIO_4);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadScenario_5")
    public void monohydrate$begin$loadScenario_5(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SCENARIO_5);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadScenario_5")
    public void monohydrate$end$loadScenario_5(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SCENARIO_5);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadScenario_6")
    public void monohydrate$begin$loadScenario_6(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SCENARIO_6);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadScenario_6")
    public void monohydrate$end$loadScenario_6(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SCENARIO_6);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadScenario_7")
    public void monohydrate$begin$loadScenario_7(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SCENARIO_7);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadScenario_7")
    public void monohydrate$end$loadScenario_7(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SCENARIO_7);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadScenario_8")
    public void monohydrate$begin$loadScenario_8(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SCENARIO_8);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadScenario_8")
    public void monohydrate$end$loadScenario_8(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SCENARIO_8);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadScenario_9")
    public void monohydrate$begin$loadScenario_9(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SCENARIO_9);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadScenario_9")
    public void monohydrate$end$loadScenario_9(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SCENARIO_9);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadScenario_10")
    public void monohydrate$begin$loadScenario_10(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SCENARIO_10);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadScenario_10")
    public void monohydrate$end$loadScenario_10(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SCENARIO_10);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadScenario_11")
    public void monohydrate$begin$loadScenario_11(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SCENARIO_11);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadScenario_11")
    public void monohydrate$end$loadScenario_11(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SCENARIO_11);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadScenario_12")
    public void monohydrate$begin$loadScenario_12(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SCENARIO_12);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadScenario_12")
    public void monohydrate$end$loadScenario_12(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SCENARIO_12);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadScenario_13")
    public void monohydrate$begin$loadScenario_13(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SCENARIO_13);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadScenario_13")
    public void monohydrate$end$loadScenario_13(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SCENARIO_13);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadScenario_14")
    public void monohydrate$begin$loadScenario_14(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_SCENARIO_14);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadScenario_14")
    public void monohydrate$end$loadScenario_14(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_SCENARIO_14);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "initPlayers")
    public void monohydrate$begin$initPlayers(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.INIT_PLAYERS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "initPlayers")
    public void monohydrate$end$initPlayers(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.INIT_PLAYERS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "buildDrawArmy")
    public void monohydrate$begin$buildDrawArmy(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.BUILD_DRAW_ARMY);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "buildDrawArmy")
    public void monohydrate$end$buildDrawArmy(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.BUILD_DRAW_ARMY);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "buildBasinsOfSeaProvinces")
    public void monohydrate$begin$buildBasinsOfSeaProvinces(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.BUILD_BASINS_OF_SEA_PROVINCES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "buildBasinsOfSeaProvinces")
    public void monohydrate$end$buildBasinsOfSeaProvinces(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.BUILD_BASINS_OF_SEA_PROVINCES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadProvinceNames_ALL")
    public void monohydrate$begin$loadProvinceNames_ALL(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_PROVINCE_NAMES_ALL);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadProvinceNames_ALL")
    public void monohydrate$end$loadProvinceNames_ALL(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_PROVINCE_NAMES_ALL);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadRegions")
    public void monohydrate$begin$loadRegions(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_REGIONS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadRegions")
    public void monohydrate$end$loadRegions(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_REGIONS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "initGameMountains")
    public void monohydrate$begin$initGameMountains(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.INIT_GAME_MOUNTAINS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "initGameMountains")
    public void monohydrate$end$initGameMountains(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.INIT_GAME_MOUNTAINS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadMountains")
    public void monohydrate$begin$loadMountains(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_MOUNTAINS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadMountains")
    public void monohydrate$end$loadMountains(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_MOUNTAINS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "addIMG2")
    public void monohydrate$begin$addIMG2(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.ADD_IMG2);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "addIMG2")
    public void monohydrate$end$addIMG2(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.ADD_IMG2);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadEventTemplates")
    public void monohydrate$begin$loadEventTemplates(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_EVENT_TEMPLATES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadEventTemplates")
    public void monohydrate$end$loadEventTemplates(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_EVENT_TEMPLATES);
        }
    }

    @Inject(
            methodName = "loadAssets",
            position = InjectPosition.BEFORE_INVOKE,
            locator = "loadDiplomacyColors_GameData")
    public void monohydrate$begin$loadDiplomacyColors_GameData(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_DIPLOMACY_COLORS_GAME_DATA);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadDiplomacyColors_GameData")
    public void monohydrate$end$loadDiplomacyColors_GameData(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_DIPLOMACY_COLORS_GAME_DATA);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadRandomAlliancesNames")
    public void monohydrate$begin$loadRandomAlliancesNames(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_RANDOM_ALLIANCES_NAMES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadRandomAlliancesNames")
    public void monohydrate$end$loadRandomAlliancesNames(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_RANDOM_ALLIANCES_NAMES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "buildColors")
    public void monohydrate$begin$buildColors(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.BUILD_COLORS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "buildColors")
    public void monohydrate$end$buildColors(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.BUILD_COLORS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "updateDrawMoveUnitsArmy")
    public void monohydrate$begin$updateDrawMoveUnitsArmy(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.UPDATE_DRAW_MOVE_UNITS_ARMY);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "updateDrawMoveUnitsArmy")
    public void monohydrate$end$updateDrawMoveUnitsArmy(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.UPDATE_DRAW_MOVE_UNITS_ARMY);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadImages")
    public void monohydrate$begin$loadImages(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_IMAGES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadImages")
    public void monohydrate$end$loadImages(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_IMAGES);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "loadMinimap")
    public void monohydrate$begin$loadMinimap(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_MINIMAP);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "loadMinimap")
    public void monohydrate$end$loadMinimap(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_MINIMAP);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "load_DeleteStatusFile")
    public void monohydrate$begin$load_DeleteStatusFile(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.LOAD_DELETE_STATUS_FILE);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "load_DeleteStatusFile")
    public void monohydrate$end$load_DeleteStatusFile(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.LOAD_DELETE_STATUS_FILE);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.BEFORE_INVOKE, locator = "updateWorldMap_Shaders")
    public void monohydrate$begin$updateWorldMap_Shaders(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.begin(Point.UPDATE_WORLD_MAP_SHADERS);
        }
    }

    @Inject(methodName = "loadAssets", position = InjectPosition.AFTER_INVOKE, locator = "updateWorldMap_Shaders")
    public void monohydrate$end$updateWorldMap_Shaders(CallbackInfo callback) {
        if (MonohydrateConfig.PROFILER_ENABLED) {
            monohydrate$profiler.end(Point.UPDATE_WORLD_MAP_SHADERS);
        }
    }
}
