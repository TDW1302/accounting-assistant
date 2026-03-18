import { Injectable } from '@angular/core';

declare const cv: any;

@Injectable({ providedIn: 'root' })
export class OpenCvService {
  private loadPromise: Promise<any> | null = null;

  load(): Promise<any> {
    if (this.loadPromise) {
      return this.loadPromise;
    }

    // Already loaded (e.g. script tag was added before)
    if (typeof cv !== 'undefined' && cv.Mat) {
      this.loadPromise = Promise.resolve(cv);
      return this.loadPromise;
    }

    this.loadPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'opencv/opencv.js';
      script.async = true;

      script.onload = () => {
        // opencv.js sets a global Module that calls onRuntimeInitialized when WASM is ready
        const waitForCv = () => {
          if (typeof cv !== 'undefined' && cv.Mat) {
            resolve(cv);
          } else {
            setTimeout(waitForCv, 50);
          }
        };
        waitForCv();
      };

      script.onerror = () => {
        this.loadPromise = null;
        reject(new Error('Failed to load OpenCV.js'));
      };

      document.head.appendChild(script);
    });

    return this.loadPromise;
  }
}
