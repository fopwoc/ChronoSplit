import ChronoSplitIosApp
import SwiftUI

struct SharedRunBoard: UIViewControllerRepresentable {
    let session: IosMobileSession
    let onSegmentClick: (() -> Void)?

    func makeUIViewController(context: Context) -> UIViewController {
        session.makeRunBoardViewController(onSegmentClick: onSegmentClick)
    }

    func updateUIViewController(_ viewController: UIViewController, context: Context) {}
}
