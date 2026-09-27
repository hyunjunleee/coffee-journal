// A stand-in for MapLibre's MLNScaleBar class, in Debug builds of the app (iosApp/project.yml leaves it out of Release)
// and in the simulator test executable (shared/build.gradle.kts).
//
// maplibre-compose's scale bar code refers to MLNScaleBar, which MapLibre.framework does not export. A release build of
// the shared Kotlin framework drops that code (the app never shows a scale bar); a debug build keeps it, in
// Kotlin/Native's cache of the maplibre-compose library, and would not link without the class. That code never runs,
// so this empty class only satisfies the linker; at launch the Objective-C runtime notes that MLNScaleBar is also
// implemented in MapLibre, and MapLibre keeps using its own.
#import <UIKit/UIKit.h>

@interface MLNScaleBar : UIView
@end

@implementation MLNScaleBar
@end
