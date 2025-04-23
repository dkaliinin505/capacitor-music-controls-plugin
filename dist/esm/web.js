import { WebPlugin } from '@capacitor/core';
export class CapacitorMusicControlsWeb extends WebPlugin {
    constructor() {
        super({
            name: 'CapacitorMusicControls',
            platforms: ['web'],
        });
    }
    create(options) {
        console.log('create', options);
        return Promise.resolve(undefined);
    }
    destroy() {
        return Promise.resolve(undefined);
    }
    updateDismissable(dismissable) {
        console.log('updateDismissable', dismissable);
    }
    updateElapsed(args) {
        console.log('updateElapsed', args);
    }
    updateIsPlaying(args) {
        console.log('updateIsPlaying', args);
    }
    updateMetadata(options) {
        console.log('updateMetadata', options);
        return Promise.resolve(undefined);
    }
}
//# sourceMappingURL=web.js.map