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

    // NSApplication.shared.helpMenu = NSMenu(title: "Help")
    //
    // // TODO: Open the URL to the website.
    // let documentationItem = NSMenuItem(title: "Documentation & Support", action: #selector(NSWorkspace.shared.open(_:)), keyEquivalent: "")
    // documentationItem.representedObject = URL(string: "https://github.com/tanin47/backdoor")
    // NSApplication.shared.helpMenu!.addItem(documentationItem)
    //
    // let helpMenuItem = NSMenuItem()
    // helpMenuItem.submenu = NSApplication.shared.helpMenu
    // mainMenu.addItem(helpMenuItem)

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


@_cdecl("openFile")
public func openFile(
    _ onFileSelected: @escaping @convention(c) (UnsafePointer<CChar>) -> Void
) {
    DispatchQueue.main.async {
        NSLog("Opening the file dialog")
        let openPanel = NSOpenPanel()
        openPanel.canChooseFiles = true
        openPanel.canChooseDirectories = false
        openPanel.allowsMultipleSelection = false

        openPanel.begin { response in
            if response == .OK {
                guard let url = openPanel.url else {
                    return
                }

                NSLog("Selected file: \(url)");
                onFileSelected(url.path.cString(using: .utf8)!)
            }
        }
    }
}

@_cdecl("saveFile")
public func saveFile(
    _ onFileSelected: @escaping @convention(c) (UnsafePointer<CChar>) -> Void
) {
    DispatchQueue.main.async {
        NSLog("Opening the save dialog")
        let savePanel = NSSavePanel()
        savePanel.canCreateDirectories = true
        savePanel.nameFieldStringValue = "untitled.csv"

        savePanel.begin { response in
            if response == .OK {
                guard let url = savePanel.url else {
                    return
                }

                NSLog("Save location selected: \(url)")
                onFileSelected(url.path.cString(using: .utf8)!)
            }
        }
    }
}

@_cdecl("startAccessingSecurityScopedResource")
public func startAccessingSecurityScopedResource(
    _ filePath: UnsafePointer<CChar>
) -> Bool {
    let path = String(cString: filePath)
    let url = URL(fileURLWithPath: path)
    NSLog("startAccessingSecurityScopedResource: \(path)")
    return url.startAccessingSecurityScopedResource()
}

@_cdecl("stopAccessingSecurityScopedResource")
public func stopAccessingSecurityScopedResource(
    _ filePath: UnsafePointer<CChar>
) -> Void {
    let path = String(cString: filePath)
    let url = URL(fileURLWithPath: path)
    NSLog("stopAccessingSecurityScopedResource: \(path)")
    url.stopAccessingSecurityScopedResource()
}
