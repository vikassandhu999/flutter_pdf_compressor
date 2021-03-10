#import "PdfCompressorPlugin.h"
#if __has_include(<pdf_compressor/pdf_compressor-Swift.h>)
#import <pdf_compressor/pdf_compressor-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "pdf_compressor-Swift.h"
#endif

@implementation PdfCompressorPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftPdfCompressorPlugin registerWithRegistrar:registrar];
}
@end
