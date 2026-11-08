package top.tasaed.aohx.monohydrate.load.mixins;

import team.rainfall.finality.luminosity2.CallbackInfo;
import team.rainfall.finality.luminosity2.annotations.Inject;
import team.rainfall.finality.luminosity2.annotations.Mixin;
import team.rainfall.finality.luminosity2.utils.InjectPosition;
import top.tasaed.aohx.monohydrate.load.ProvinceFileCache;

@Mixin(mixinClass = "age.of.civilizations2.jakowski.lukasz.Menus.Load.Menu_LoadMap")
public class MixinLoadMapCandidates {
    private boolean monohydrate$candidatesComplete;

    @Inject(methodName = "loadMap", position = InjectPosition.HEAD)
    public void monohydrate$beginCandidates(CallbackInfo callback) {
        if (!monohydrate$candidatesComplete) ProvinceFileCache.beginFrame(this);
    }

    @Inject(methodName = "loadMap", position = InjectPosition.RETURN)
    public void monohydrate$endCandidatesFrame(CallbackInfo callback) {
        ProvinceFileCache.endFrame(this);
    }

    @Inject(methodName = "loadMap", position = InjectPosition.AFTER_INVOKE, locator = "setMenuID")
    public void monohydrate$completeCandidates(CallbackInfo callback) {
        monohydrate$candidatesComplete = true;
        ProvinceFileCache.endSession(this);
    }
}
