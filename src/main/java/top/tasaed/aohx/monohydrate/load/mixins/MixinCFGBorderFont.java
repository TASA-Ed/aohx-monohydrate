package top.tasaed.aohx.monohydrate.load.mixins;

import age.of.civilizations2.jakowski.lukasz.CFG;
import team.rainfall.finality.luminosity2.CallbackInfo;
import team.rainfall.finality.luminosity2.annotations.Inject;
import team.rainfall.finality.luminosity2.annotations.Mixin;
import team.rainfall.finality.luminosity2.utils.InjectPosition;
import top.tasaed.aohx.monohydrate.core.MonohydrateConfig;
import top.tasaed.aohx.monohydrate.load.SharedBorderFont;

@Mixin(mixinClass = "age.of.civilizations2.jakowski.lukasz.CFG")
public class MixinCFGBorderFont {
    @Inject(methodName = "loadFontBorder", position = InjectPosition.HEAD)
    public static void monohydrate$releaseBorderFont2(CallbackInfo callback) {
        if (!MonohydrateConfig.BORDER_FONT_SHARING_ENABLED) return;
        // Release the borrower before the original method disposes the texture owner.
        if (CFG.fontBorder2 != null) {
            CFG.fontBorder2.dispose();
            CFG.fontBorder2 = null;
        }
    }

    @Inject(
            methodName = "loadFontBorder",
            position = InjectPosition.BEFORE_INVOKE,
            locator = "loadFontBorder2",
            returnWithValue = true)
    public static void monohydrate$shareBorderFont(CallbackInfo callback) {
        if (!MonohydrateConfig.BORDER_FONT_SHARING_ENABLED) return;
        if (!SharedBorderFont.canShare(CFG.fontBorder)) return;
        CFG.fontBorder.setOwnsTexture(true);
        CFG.fontBorder2 = SharedBorderFont.copy(CFG.fontBorder);
        callback.cancel();
    }

    @Inject(methodName = "loadFontBorder2", position = InjectPosition.HEAD, returnWithValue = true)
    public static void monohydrate$reloadBorderFonts(CallbackInfo callback) {
        if (!MonohydrateConfig.BORDER_FONT_SHARING_ENABLED) return;
        // Let incremental fonts retain their own generator instead of recursing through the owner.
        if (!SharedBorderFont.canShare(CFG.fontBorder)) return;
        // A direct reload must regenerate the owner too, never copy its render-time scale.
        CFG.loadFontBorder();
        callback.cancel();
    }
}
