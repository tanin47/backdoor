import Foundation
import os.log
import AppKit
import CoreGraphics

@_cdecl("setupMenu")
public func setupMenu() {
    let mainMenu = NSMenu()

    let appMenuItem = NSMenuItem()
    let appMenu = NSMenu()
    appMenuItem.submenu = appMenu

    appMenu.addItem(NSMenuItem(title: "About Backdoor", action: #selector(NSApplication.orderFrontStandardAboutPanel(_:)), keyEquivalent: ""))
    appMenu.addItem(NSMenuItem.separator())

    NSApplication.shared.servicesMenu = NSMenu()
    let servicesMenuItem = NSMenuItem(title: "Services", action: nil, keyEquivalent: "")
    servicesMenuItem.submenu = NSApp.servicesMenu
    appMenu.addItem(servicesMenuItem)

    appMenu.addItem(NSMenuItem.separator())
    appMenu.addItem(NSMenuItem(title: "Hide Backdoor", action: #selector(NSApplication.hide(_:)), keyEquivalent: "h"))
    appMenu.addItem(NSMenuItem(title: "Hide Others", action: #selector(NSApplication.hideOtherApplications(_:)), keyEquivalent: "h"))
    appMenu.addItem(NSMenuItem(title: "Show All", action: #selector(NSApplication.unhideAllApplications(_:)), keyEquivalent: ""))
    appMenu.addItem(NSMenuItem.separator())

    appMenu.addItem(NSMenuItem(title: "Quit Backdoor", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))
    mainMenu.addItem(appMenuItem)

    let editMenuItem = NSMenuItem()
    let editMenu = NSMenu(title: "Edit")
    editMenuItem.submenu = editMenu

    editMenu.addItem(NSMenuItem(title: "Undo", action: #selector(UndoManager.undo), keyEquivalent: "z"))
    editMenu.addItem(NSMenuItem(title: "Redo", action: #selector(UndoManager.redo), keyEquivalent: "Z"))
    editMenu.addItem(NSMenuItem.separator())
    editMenu.addItem(NSMenuItem(title: "Copy", action: #selector(NSText.copy(_:)), keyEquivalent: "c"))
    editMenu.addItem(NSMenuItem(title: "Cut", action: #selector(NSText.cut(_:)), keyEquivalent: "x"))
    editMenu.addItem(NSMenuItem(title: "Paste", action: #selector(NSText.paste(_:)), keyEquivalent: "v"))
    editMenu.addItem(NSMenuItem(title: "Select All", action: #selector(NSText.selectAll(_:)), keyEquivalent: "a"))

    mainMenu.addItem(editMenuItem)

    NSApplication.shared.windowsMenu = NSMenu(title: "Window")
    let windowMenuItem = NSMenuItem()
    windowMenuItem.submenu = NSApplication.shared.windowsMenu
    mainMenu.addItem(windowMenuItem)

    NSApplication.shared.helpMenu = NSMenu(title: "Help")

    // TODO: Open the URL to the website.
    let documentationItem = NSMenuItem(title: "Documentation & Support", action: #selector(NSWorkspace.shared.open(_:)), keyEquivalent: "")
    NSApplication.shared.helpMenu!.addItem(documentationItem)

    let helpMenuItem = NSMenuItem()
    helpMenuItem.submenu = NSApplication.shared.helpMenu
    mainMenu.addItem(helpMenuItem)

    NSApplication.shared.mainMenu = mainMenu
}


@_cdecl("nsWindowMakeKeyAndOrderFront")
public func nsWindowMakeKeyAndOrderFront() {
    NSLog("Activating Backdoor's window")
    NSApp.activate(ignoringOtherApps: true)

    if let window = NSApp.windows.first {
        NSLog("Set the resize mask")
        window.styleMask.insert([.resizable, .titled])
    }
}
