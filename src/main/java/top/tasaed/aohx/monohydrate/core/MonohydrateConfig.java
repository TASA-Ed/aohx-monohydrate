package top.tasaed.aohx.monohydrate.core;

/** JVM switches are read once, outside mixins whose initializers are not copied by Luminosity. */
public final class MonohydrateConfig {
    public static final boolean CIM_HEADER_READ_ENABLED =
            !Boolean.getBoolean("DisableMonohydrateOptimizations") && !Boolean.getBoolean("DisableCimHeaderRead");
    public static final boolean NATIVE_TEXTURE_FORMAT_ENABLED = !Boolean.getBoolean("DisableMonohydrateOptimizations")
            && !Boolean.getBoolean("DisableMonohydrateNativeTextureFormat");
    public static final boolean CITY_OPTIMIZATIONS_ENABLED = !Boolean.getBoolean("DisableMonohydrateOptimizations")
            && !Boolean.getBoolean("DisableMonohydrateCityOptimizations");
    public static final boolean PROVINCE_CANDIDATE_FILTER_ENABLED =
            !Boolean.getBoolean("DisableMonohydrateOptimizations")
                    && !Boolean.getBoolean("DisableMonohydrateProvinceCandidateFilter");
    public static final boolean BORDER_FONT_SHARING_ENABLED = !Boolean.getBoolean("DisableMonohydrateOptimizations")
            && !Boolean.getBoolean("DisableMonohydrateBorderFontSharing");
    public static final boolean PROFILER_ENABLED = !Boolean.getBoolean("DisableMonohydrateProfiler");

    private MonohydrateConfig() {}
}
