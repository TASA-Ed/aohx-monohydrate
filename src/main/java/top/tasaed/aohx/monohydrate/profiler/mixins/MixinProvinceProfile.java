package top.tasaed.aohx.monohydrate.profiler.mixins;

import team.rainfall.finality.luminosity2.CallbackInfo;
import team.rainfall.finality.luminosity2.annotations.Inject;
import team.rainfall.finality.luminosity2.annotations.Mixin;
import team.rainfall.finality.luminosity2.annotations.Shadow;
import team.rainfall.finality.luminosity2.utils.InjectPosition;
import top.tasaed.aohx.monohydrate.load.ProvinceFileCache;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler;
import top.tasaed.aohx.monohydrate.profiler.LoadingProfiler.Stage;

@Mixin(mixinClass = "age.of.civilizations2.jakowski.lukasz.Province")
public class MixinProvinceProfile {
    @Shadow
    private int iProvinceID;

    @Inject(methodName = "buildProvinceBG", position = InjectPosition.HEAD)
    public void monohydrate$invalidateTextureCandidates(boolean update, CallbackInfo callback) {
        ProvinceFileCache.invalidateTextures();
    }

    @Inject(methodName = "buildProvinceBG", position = InjectPosition.RETURN)
    public void monohydrate$rebuiltTextureCandidates(boolean update, CallbackInfo callback) {
        ProvinceFileCache.invalidateTextures();
    }

    @Inject(methodName = "loadProvinceBG", position = InjectPosition.BEFORE_INVOKE, locator = "loadFile")
    public void monohydrate$beginTexturePath(CallbackInfo callback) {
        LoadingProfiler.beginPathStage(Stage.TEXTURE_PATH, iProvinceID);
    }

    @Inject(methodName = "loadProvinceBG", position = InjectPosition.AFTER_INVOKE, locator = "loadFile")
    public void monohydrate$endTexturePath(CallbackInfo callback) {
        LoadingProfiler.endStage(Stage.TEXTURE_PATH);
    }

    @Inject(methodName = "loadProvinceBG", position = InjectPosition.BEFORE_INVOKE, locator = "readCIM")
    public void monohydrate$beginTextureCim(CallbackInfo callback) {
        LoadingProfiler.beginStage(Stage.TEXTURE_CIM);
    }

    // This single upload segment includes both Texture(Pixmap) and Image construction, texture
    // filter/wrap configuration and assignment to provBG, but excludes CIM decoding and disposal.
    // Avoid <init> locators, which would also include path StringBuilder construction:
    // Luminosity matches names only, including constructors of its own injected CallbackInfo.
    @Inject(methodName = "loadProvinceBG", position = InjectPosition.AFTER_INVOKE, locator = "readCIM")
    public void monohydrate$beginTextureUpload(CallbackInfo callback) {
        LoadingProfiler.endStage(Stage.TEXTURE_CIM);
        LoadingProfiler.beginStage(Stage.TEXTURE_UPLOAD);
    }

    @Inject(methodName = "loadProvinceBG", position = InjectPosition.BEFORE_INVOKE, locator = "dispose")
    public void monohydrate$endTextureUpload(CallbackInfo callback) {
        LoadingProfiler.endStage(Stage.TEXTURE_UPLOAD);
    }

    // Count fallback entry even when the rebuild throws. The recursive reload starts its own
    // stages only after a successful rebuild returns; no game retry behavior is changed here.
    @Inject(methodName = "loadProvinceBG", position = InjectPosition.BEFORE_INVOKE, locator = "buildProvinceBG")
    public void monohydrate$beginTextureRebuild(CallbackInfo callback) {
        LoadingProfiler.beginStage(Stage.TEXTURE_REBUILD);
    }

    @Inject(methodName = "loadProvinceBG", position = InjectPosition.AFTER_INVOKE, locator = "buildProvinceBG")
    public void monohydrate$endTextureRebuild(CallbackInfo callback) {
        LoadingProfiler.endStage(Stage.TEXTURE_REBUILD);
    }
}
