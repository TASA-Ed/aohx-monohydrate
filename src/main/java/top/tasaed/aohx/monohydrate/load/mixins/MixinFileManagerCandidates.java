package top.tasaed.aohx.monohydrate.load.mixins;

import team.rainfall.finality.luminosity2.CallbackInfo;
import team.rainfall.finality.luminosity2.annotations.Inject;
import team.rainfall.finality.luminosity2.annotations.Mixin;
import team.rainfall.finality.luminosity2.utils.InjectPosition;
import top.tasaed.aohx.monohydrate.load.ProvinceFileCache;

@Mixin(mixinClass = "age.of.civilizations2.jakowski.lukasz.Files.FileManager")
public class MixinFileManagerCandidates {
    @Inject(methodName = "initLoadInterface", position = InjectPosition.RETURN)
    public static void monohydrate$installCandidates(CallbackInfo callback) {
        ProvinceFileCache.install();
    }
}
