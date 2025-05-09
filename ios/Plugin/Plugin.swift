import Foundation
import Capacitor
import AVFoundation
import MediaPlayer;

extension DispatchQueue {

     static func background(delay: Double = 0.0, background: (()->Void)? = nil, completion: (() -> Void)? = nil) {
         DispatchQueue.global(qos: .background).async {
             background?()
             if let completion = completion {
                 DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: {
                     completion()
                 })
             }
         }
     }

 }

 
/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */
@objc(CapacitorMusicControls)
public class CapacitorMusicControls: CAPPlugin {
    
    var musicControlsInfo: CapacitorMusicControlsInfo!;
    var eventListnerActive = false;


    func setupAudioSession() {
        print("Setting up audio session for music controls")
        do {
            // Use the most basic configuration first
            try AVAudioSession.sharedInstance().setCategory(.playback)

            // Try activating with no options first
            try AVAudioSession.sharedInstance().setActive(true)

            print("Audio session setup successful")
        } catch {
            print("Failed to set up audio session: \(error.localizedDescription)")
        }
    }
 
    @objc func create(_ call: CAPPluginCall) {

        setupAudioSession()

        let options: Dictionary = call.options;

        self.musicControlsInfo = CapacitorMusicControlsInfo(dictionary: options as NSDictionary);
        
        
        print("MusicControlsOptions:")
        for optionLine in options {
          print(optionLine)
        }

        if(!self.eventListnerActive){
            self.registerMusicControlsEventListener();
        }

        var nowPlayingInfo = [String: Any]()
    
        let duration = self.musicControlsInfo.duration;
        let elapsed = self.musicControlsInfo.elapsed;
        let playbackRate = self.musicControlsInfo.isPlaying;

        let nowPlayingInfoCenter = MPNowPlayingInfoCenter.default();

        nowPlayingInfo = [
            MPMediaItemPropertyArtist: self.musicControlsInfo.artist ?? "",
            MPMediaItemPropertyTitle: self.musicControlsInfo.track ?? "",
            MPMediaItemPropertyAlbumTitle: self.musicControlsInfo.album ?? "",
            MPMediaItemPropertyPlaybackDuration: duration ?? 0,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: elapsed ?? 0,
            MPNowPlayingInfoPropertyPlaybackRate: playbackRate ?? 1.0
        ]
        

        if(self.musicControlsInfo.cover != nil){

            let mediaItemArtwork = self.createCoverArtwork(coverUri: self.musicControlsInfo.cover!);
            if(mediaItemArtwork != nil){
                nowPlayingInfo[MPMediaItemPropertyArtwork] = mediaItemArtwork;
            }
        }
            
        nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo

        call.resolve();
        
    }

    @objc func updateMetadata(_ call: CAPPluginCall) {
        print("Updating metadata without recreating controls")
        let options: Dictionary = call.options;

        // Log what we're receiving
        print("Metadata update options:")
        for (key, value) in options {
            print("MusicControls: \(key): \(value)")
        }

        // Update the musicControlsInfo object
        self.musicControlsInfo = CapacitorMusicControlsInfo(dictionary: options as NSDictionary);

        // Update the Now Playing info
        let nowPlayingInfoCenter = MPNowPlayingInfoCenter.default();
        var nowPlayingInfo = nowPlayingInfoCenter.nowPlayingInfo ?? [String: Any]()

        // Update metadata properties
        nowPlayingInfo[MPMediaItemPropertyArtist] = self.musicControlsInfo.artist ?? "";
        nowPlayingInfo[MPMediaItemPropertyTitle] = self.musicControlsInfo.track ?? "";
        nowPlayingInfo[MPMediaItemPropertyAlbumTitle] = self.musicControlsInfo.album ?? "";

        // Only update duration if provided
        if let duration = self.musicControlsInfo.duration {
            nowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = duration;
        }

        // Update cover art if provided
        if let coverUri = self.musicControlsInfo.cover, !coverUri.isEmpty {
            if let mediaItemArtwork = self.createCoverArtwork(coverUri: coverUri) {
                nowPlayingInfo[MPMediaItemPropertyArtwork] = mediaItemArtwork;
            }
        }

        // Update the now playing info
        nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo;

        call.resolve();
    }
    
    
    @objc func updateIsPlaying(_ call: CAPPluginCall) {
        
        let options: Dictionary = call.options;
        
        musicControlsInfo = CapacitorMusicControlsInfo(dictionary: options as NSDictionary);
        
        let elapsed = self.musicControlsInfo.elapsed;
        let playbackRate = self.musicControlsInfo.isPlaying;
        

        let nowPlayingInfoCenter = MPNowPlayingInfoCenter.default();
        var nowPlayingInfo = nowPlayingInfoCenter.nowPlayingInfo;
 
 
        nowPlayingInfo?[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsed;
        nowPlayingInfo?[MPNowPlayingInfoPropertyPlaybackRate] = playbackRate;
        
        nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo

        call.resolve();

    }
    
    @objc func updateElapsed(_ call: CAPPluginCall) {
        
         self.updateIsPlaying(call);
         
     }
    
    
    @objc func listen(_ call: CAPPluginCall) {
            self.registerMusicControlsEventListener();
            
            call.resolve();
   }
       
    
    @objc func destroy(_ call: CAPPluginCall) {
        self.deregisterMusicControlsEventListener();
        
        call.resolve();
           
    }
 
    
    func createCoverArtwork(coverUri : String) -> MPMediaItemArtwork? {
        
        var coverImage: UIImage?;
        print("Cover URI provided: \(coverUri)")

        if (coverUri.hasPrefix("http://") || coverUri.hasPrefix("https://")) {
            //print("Cover item is a URL");

            let coverImageUrl = URL(string: coverUri)!;
            
            do{

                let coverImageData = try Data(contentsOf: coverImageUrl);
                coverImage = UIImage(data:coverImageData)!;
 
            } catch {
               // print("Could not make image");
                coverImage = nil;
            }
        }
        else if (coverUri.hasPrefix("file://")) {
            //print("coverImage file://");

            
            let fullCoverImagePath = coverUri.replacingOccurrences(of: "file://", with: "");
            
            let defaultManager = FileManager.default;
            
            if(defaultManager.fileExists(atPath: fullCoverImagePath)){
                coverImage = UIImage(contentsOfFile: fullCoverImagePath)!
            } else {
                coverImage = nil;

               // print("unable to find coverImage");
            }
 
        }
        else if (coverUri != "") {
           //  print("coverImage empty");

//            let baseCoverImagePath = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0];
//            let fullCoverImagePath = String(format:"%@%@", baseCoverImagePath, coverUri);
//
            let defaultManager = FileManager.default;
            
            let coverFilePath = "public/" + coverUri;
            
            let urlPath = Bundle.main.path(forResource: coverFilePath, ofType: "");

 
            if(urlPath != nil && defaultManager.fileExists(atPath: urlPath!)){
               coverImage = UIImage(contentsOfFile: urlPath!) ?? UIImage();
            } else {
                coverImage = nil;
            }

        }
        else {
            coverImage = nil;
        }
        
        if(coverImage != nil && self.isCoverImageValid(inputImage: coverImage!)){

            return MPMediaItemArtwork.init(boundsSize: coverImage!.size, requestHandler: { (size) -> UIImage in
                    return coverImage!
            })
        } else {
            return nil;
        }
                
    }
    
    func isCoverImageValid(inputImage: UIImage) -> Bool {
        
        let cii = CIImage(image: inputImage);
        
        return inputImage != nil && cii != nil;

    }
    
    @objc func changedThumbSliderOnLockScreen(_ event: MPChangePlaybackPositionCommandEvent) -> MPRemoteCommandHandlerStatus {
        self.notifyListeners("controlsNotification", data: [ "message" : "music-controls-seek-to", "position" : event.positionTime ])
        return .success;
    }
    
    @objc func skipForwardEvent(_ event: MPSkipIntervalCommandEvent) -> MPRemoteCommandHandlerStatus{
        self.notifyListeners("controlsNotification", data: [ "message" : "music-controls-skip-forward" ])
        return .success;
    }
    
    @objc func skipBackwardEvent(_ event: MPSkipIntervalCommandEvent) -> MPRemoteCommandHandlerStatus{
        self.notifyListeners("controlsNotification", data: [ "message" : "music-controls-skip-backward" ])
        return .success;

    }
    
    @objc func nextTrackEvent(_ event: MPRemoteCommandEvent) -> MPRemoteCommandHandlerStatus {
        self.notifyListeners("controlsNotification", data: [ "message" : "music-controls-next" ])
        return .success;
    }
    
    @objc func prevTrackEvent(_ event: MPRemoteCommandEvent) -> MPRemoteCommandHandlerStatus {
        self.notifyListeners("controlsNotification", data: [ "message" : "music-controls-previous" ])
        return .success;
    }
    
    @objc func pauseEvent(_ event: MPRemoteCommandEvent) -> MPRemoteCommandHandlerStatus {
        self.notifyListeners("controlsNotification", data: [ "message" : "music-controls-pause" ])
        return .success;
    }
    
   @objc func playEvent(_ event: MPRemoteCommandEvent) -> MPRemoteCommandHandlerStatus {
        self.notifyListeners("controlsNotification", data: [ "message" : "music-controls-play" ])
        return .success;
    }
    
    
    func registerMusicControlsEventListener(){
        
        print("Registering music controls event listener")
        self.eventListnerActive = true

        DispatchQueue.main.async {
            UIApplication.shared.beginReceivingRemoteControlEvents()
            print("Begin receiving remote control events")
        }
        
        let commandCenter = MPRemoteCommandCenter.shared();
           
        commandCenter.playCommand.isEnabled = true;
        commandCenter.playCommand.addTarget(self, action: #selector(CapacitorMusicControls.playEvent(_:)));
        commandCenter.pauseCommand.isEnabled = true;
        commandCenter.pauseCommand.addTarget(self, action: #selector(CapacitorMusicControls.pauseEvent(_:)));
        
        if(self.musicControlsInfo.hasNext == true){
            commandCenter.nextTrackCommand.isEnabled = true;
            commandCenter.nextTrackCommand.addTarget(self, action: #selector(CapacitorMusicControls.nextTrackEvent(_:)));
        }
        
        if(self.musicControlsInfo.hasPrev == true){
            commandCenter.previousTrackCommand.isEnabled = true;
            commandCenter.previousTrackCommand.addTarget(self, action: #selector(CapacitorMusicControls.prevTrackEvent(_:)));
        }
        
        if(self.musicControlsInfo.hasSkipBackward == true){
            commandCenter.skipBackwardCommand.isEnabled = true;
            commandCenter.skipBackwardCommand.preferredIntervals = [self.musicControlsInfo.skipBackwardInterval!];
            commandCenter.skipBackwardCommand.addTarget(self, action: #selector(CapacitorMusicControls.skipBackwardEvent(_:)));
        }
        
        if(self.musicControlsInfo.hasSkipForward == true){
            commandCenter.skipForwardCommand.isEnabled = true;
            commandCenter.skipForwardCommand.preferredIntervals = [self.musicControlsInfo.skipForwardInterval!];
            commandCenter.skipForwardCommand.addTarget(self, action: #selector(CapacitorMusicControls.skipForwardEvent(_:)));
        }
        
        if(self.musicControlsInfo.hasScrubbing == true){
            commandCenter.changePlaybackPositionCommand.isEnabled = true;
            commandCenter.changePlaybackPositionCommand.addTarget(self, action: #selector(CapacitorMusicControls.changedThumbSliderOnLockScreen(_:)));
        }
    }
    
    func deregisterMusicControlsEventListener(){
        
        self.eventListnerActive = false;
        
        DispatchQueue.main.async { // Correct
            UIApplication.shared.endReceivingRemoteControlEvents();
        }
            
         let commandCenter = MPRemoteCommandCenter.shared();

        commandCenter.playCommand.isEnabled = false;
        commandCenter.playCommand.removeTarget(self);
        commandCenter.pauseCommand.isEnabled = false;
        commandCenter.pauseCommand.removeTarget(self);

        if(commandCenter.nextTrackCommand.isEnabled){

            commandCenter.nextTrackCommand.isEnabled = false;
            commandCenter.nextTrackCommand.removeTarget(self);

        }

        if(commandCenter.previousTrackCommand.isEnabled){

            commandCenter.previousTrackCommand.isEnabled = false;
            commandCenter.previousTrackCommand.removeTarget(self);

        }
        
        if(commandCenter.changePlaybackPositionCommand.isEnabled){
            commandCenter.changePlaybackPositionCommand.isEnabled = false;
            commandCenter.changePlaybackPositionCommand.removeTarget(self);
        }
        
        if(commandCenter.skipBackwardCommand.isEnabled){
            commandCenter.skipBackwardCommand.isEnabled = false;
            commandCenter.skipBackwardCommand.removeTarget(self);
        }
        
        if(commandCenter.skipForwardCommand.isEnabled){
            commandCenter.skipForwardCommand.isEnabled = false;
            commandCenter.skipForwardCommand.removeTarget(self);
        }
        
    }
    
    deinit {
        self.deregisterMusicControlsEventListener();
    }
    
}
