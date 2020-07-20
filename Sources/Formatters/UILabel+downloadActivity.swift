//
//  UILabel+download.swift
//  ___PACKAGENAME___
//
//  Created by ___FULLUSERNAME___ on ___DATE___
//  ___COPYRIGHT___

import UIKit
import QMobileUI
import Alamofire
import XCGLogger

extension UILabel {

    @objc dynamic public var downloadActivityAction: String? {
        get {
            return self.text
        }
        set {
            self.text = newValue
            if let newValue = newValue, URL(string: newValue) != nil {
                let tap = UITapGestureRecognizer(target: self, action: #selector(downloadActivityActionTap(_:)))
                self.isUserInteractionEnabled = true
                self.addGestureRecognizer(tap)
            } else {
                for gesture in self.gestureRecognizers ?? [] {
                    self.removeGestureRecognizer(gesture)
                }
                self.isUserInteractionEnabled = false
            }
        }
    }

    @objc func downloadActivityActionTap(_ sender: Any) {
        guard let urlString = self.text, let url = URL(string: urlString) else {
            return
        }
        AF.request(url).responseData { response in
            if let fileData = response.data {
                let activityViewController = UIActivityViewController(activityItems: [url.lastPathComponent, fileData], applicationActivities: nil)
                activityViewController.sourceView = self
                UIApplication.topViewController?.present(activityViewController, animated: true) {
                    logger.info("End to download \(url)")
                }
            }
        }
    }
}
