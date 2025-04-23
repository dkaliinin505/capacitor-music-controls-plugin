import { PluginListenerHandle } from "@capacitor/core";
export interface CapacitorMusicControlsInfo {
    track?: string;
    artist?: string;
    cover?: string;
    isPlaying?: boolean;
    dismissable?: boolean;
    hasPrev?: boolean;
    hasNext?: boolean;
    hasSkipForward?: boolean;
    hasSkipBackward?: boolean;
    skipForwardInterval?: number;
    skipBackwardInterval?: number;
    hasScrubbing?: boolean;
    hasClose?: boolean;
    album?: string;
    duration?: number;
    elapsed?: number;
    ticker?: string;
    playIcon?: string;
    pauseIcon?: string;
    prevIcon?: string;
    nextIcon?: string;
    closeIcon?: string;
    notificationIcon?: string;
}
export interface CapacitorMusicControlsPlugin {
    /**
     * Create the media controls
     * @param options {CapacitorMusicControlsInfo}
     * @returns {Promise<any>}
     */
    create(options: CapacitorMusicControlsInfo): Promise<any>;
    /**
     * Destroy the media controller
     * @returns {Promise<any>}
     */
    destroy(): Promise<any>;
    /**
     * Toggle play/pause:
     * @param args {Object}
     */
    updateIsPlaying(args: {
        isPlaying: boolean;
    }): void;
    /**
     * Update elapsed time, optionally toggle play/pause:
     * @param args {Object}
     */
    updateElapsed(args: {
        elapsed: number;
        isPlaying: boolean;
    }): void;
    /**
     * Toggle dismissable:
     * @param dismissable {boolean}
     */
    updateDismissable(dismissable: boolean): void;
    /**
     * Add a listener for events from the native layer
     * @param event {string} The event name
     * @param callback {Function} The callback function to be called when the event fires
     */
    addListener(event: string, callback: (info: any) => void): Promise<PluginListenerHandle>;
    /**
     * Update track metadata without recreating controls
     * @param options {CapacitorMusicControlsInfo}
     * @returns {Promise<any>}
     */
    updateMetadata(options: CapacitorMusicControlsInfo): Promise<any>;
}
