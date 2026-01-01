import SwiftUI
import UIKit
import KawaiiShared

struct KawaiiRoot: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // Kotlin top-level function in MainViewController.kt becomes MainViewControllerKt.MainViewController()
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct KawaiiRawEditorApp: App {
    var body: some Scene {
        WindowGroup {
            KawaiiRoot()
                .ignoresSafeArea()
        }
    }
}
