# Monohydrate 项目指南

本项目为 Finality Framework 插件，为《历史时代 2：DE》（Age of History II: Definitive Edition）游戏提供了性能优化。

可参考 `ref/` 下的源代码，`ref/aoh2de/` 为游戏代码，`ref/loader/` 为 Finality Framework Loader 代码。（部分环境下可能没有此文件夹）

## 规范
 
- 编辑代码后运行 `gradlew.bat spotlessApply` 和 `gradlew.bat build` 保证代码无误。
- 日志使用 `team.rainfall.finality.FinalityLogger`，输出错误堆栈时使用 `FinalityLogger.error(message, throwable)`，不要使用其他方法。

## 与游戏联调

因为游戏运行需要 Gdx.files 和对应资源才可运行，所以与游戏联合调试需要在 Steam 游戏目录下进行，不要引用 `libs/` 下的 jar 进行。

因为需要打包依赖，所以首先运行 `gradlew.bat clean release`，生成的 all jar 包需要放入 `[游戏目录]/mods/[模组名称]/plugins/` 中。

然后运行游戏目录下的 Finality Loader（命名一般类似 `loader-1.x.x.jar`），然后等待游戏加载，可能需要较长时间。

如果用户没有提供 Steam 游戏目录和模组名称，则默认无需调试。
