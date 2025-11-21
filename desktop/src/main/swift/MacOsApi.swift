import Foundation
import os.log
import AppKit

@_cdecl("setupMenu")
public func setupMenu() {
    let mainMenu = NSMenu()

    let appMenuItem = NSMenuItem()
    let appMenu = NSMenu()
    appMenuItem.submenu = appMenu

    appMenu.addItem(NSMenuItem(title: "About App", action: #selector(NSApplication.orderFrontStandardAboutPanel(_:)), keyEquivalent: ""))
    appMenu.addItem(NSMenuItem.separator())
    appMenu.addItem(NSMenuItem(title: "Quit", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))

    let fileMenuItem = NSMenuItem()
    let fileMenu = NSMenu(title: "File")
    fileMenuItem.submenu = fileMenu

    fileMenu.addItem(NSMenuItem(title: "DoNothing", action: nil, keyEquivalent: "d"))

    mainMenu.addItem(appMenuItem)
    mainMenu.addItem(fileMenuItem)

    NSApplication.shared.mainMenu = mainMenu
}
