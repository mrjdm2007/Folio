import AppKit
import Foundation

let output = CommandLine.arguments.dropFirst().first ?? "/tmp/folio-icon.png"
let canvas = NSSize(width: 1024, height: 1024)
let image = NSImage(size: canvas)
image.lockFocus()
let bounds = NSRect(origin: .zero, size: canvas)

NSColor(calibratedRed: 10.0/255, green: 13.0/255, blue: 11.0/255, alpha: 1).setFill()
NSBezierPath(roundedRect: bounds.insetBy(dx: 10, dy: 10), xRadius: 220, yRadius: 220).fill()

let page = NSRect(x: 230, y: 145, width: 565, height: 735)
NSColor(calibratedRed: 25.0/255, green: 34.0/255, blue: 27.0/255, alpha: 1).setFill()
NSBezierPath(roundedRect: page, xRadius: 58, yRadius: 58).fill()

NSColor(calibratedRed: 185.0/255, green: 243.0/255, blue: 106.0/255, alpha: 1).setFill()
NSBezierPath(roundedRect: NSRect(x: 230, y: 145, width: 20, height: 735), xRadius: 10, yRadius: 10).fill()

let fold = NSBezierPath()
fold.move(to: NSPoint(x: 655, y: 880))
fold.line(to: NSPoint(x: 795, y: 740))
fold.line(to: NSPoint(x: 655, y: 740))
fold.close()
NSColor(calibratedRed: 1, green: 184.0/255, blue: 92.0/255, alpha: 1).setFill()
fold.fill()

let letter = NSAttributedString(string: "F", attributes: [
    .font: NSFont.systemFont(ofSize: 475, weight: .heavy),
    .foregroundColor: NSColor(calibratedRed: 185.0/255, green: 243.0/255, blue: 106.0/255, alpha: 1)
])
letter.draw(at: NSPoint(x: 350, y: 270))

NSColor(calibratedRed: 141.0/255, green: 216.0/255, blue: 162.0/255, alpha: 1).setFill()
NSBezierPath(roundedRect: NSRect(x: 330, y: 205, width: 355, height: 12), xRadius: 6, yRadius: 6).fill()
image.unlockFocus()

guard let tiff = image.tiffRepresentation,
      let bitmap = NSBitmapImageRep(data: tiff),
      let png = bitmap.representation(using: .png, properties: [:]) else {
    fatalError("Could not render Folio icon")
}
try png.write(to: URL(fileURLWithPath: output), options: .atomic)
