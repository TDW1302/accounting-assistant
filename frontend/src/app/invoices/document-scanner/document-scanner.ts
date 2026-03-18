import {
  Component,
  ElementRef,
  EventEmitter,
  inject,
  Input,
  OnDestroy,
  Output,
  ViewChild,
  AfterViewInit,
} from '@angular/core';
import { OpenCvService } from '../../services/opencv.service';

interface Point {
  x: number;
  y: number;
}

@Component({
  selector: 'app-document-scanner',
  templateUrl: './document-scanner.html',
  styleUrl: './document-scanner.scss',
})
export class DocumentScanner implements AfterViewInit, OnDestroy {
  private readonly openCvService = inject(OpenCvService);

  @Input() imageFile!: File;
  @Output() scanned = new EventEmitter<Blob>();
  @Output() cancelled = new EventEmitter<void>();

  @ViewChild('sourceCanvas') sourceCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('previewCanvas') previewCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('svgOverlay') svgOverlay!: ElementRef<SVGSVGElement>;

  corners: Point[] = [];
  loading = true;
  showPreview = false;
  private cv: any;
  private sourceImage: HTMLImageElement | null = null;
  private draggingIndex = -1;
  private canvasRect: DOMRect | null = null;

  // Display dimensions (canvas/SVG size)
  displayWidth = 0;
  displayHeight = 0;

  // Scale factor from display to original image
  private scaleX = 1;
  private scaleY = 1;

  ngAfterViewInit(): void {
    this.loadImageAndDetect();
  }

  ngOnDestroy(): void {
    this.sourceImage = null;
  }

  private async loadImageAndDetect(): Promise<void> {
    const img = new Image();
    img.onload = async () => {
      this.sourceImage = img;
      this.drawSourceImage(img);

      try {
        this.cv = await this.openCvService.load();
        this.detectEdges(img);
      } catch {
        this.setDefaultCorners();
      }
      this.loading = false;
    };
    img.src = URL.createObjectURL(this.imageFile);
  }

  private drawSourceImage(img: HTMLImageElement): void {
    const canvas = this.sourceCanvas.nativeElement;
    const maxWidth = Math.min(window.innerWidth - 40, 800);
    const maxHeight = window.innerHeight - 200;

    const scale = Math.min(maxWidth / img.width, maxHeight / img.height, 1);
    this.displayWidth = Math.round(img.width * scale);
    this.displayHeight = Math.round(img.height * scale);

    canvas.width = this.displayWidth;
    canvas.height = this.displayHeight;

    this.scaleX = img.width / this.displayWidth;
    this.scaleY = img.height / this.displayHeight;

    const ctx = canvas.getContext('2d')!;
    ctx.drawImage(img, 0, 0, this.displayWidth, this.displayHeight);
  }

  private detectEdges(img: HTMLImageElement): void {
    const cv = this.cv;
    const canvas = this.sourceCanvas.nativeElement;
    const src = cv.imread(canvas);
    const gray = new cv.Mat();
    const blurred = new cv.Mat();
    const edges = new cv.Mat();
    const dilated = new cv.Mat();
    const contours = new cv.MatVector();
    const hierarchy = new cv.Mat();

    try {
      cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY);
      cv.GaussianBlur(gray, blurred, new cv.Size(5, 5), 0);
      cv.Canny(blurred, edges, 50, 150);

      const kernel = cv.Mat.ones(3, 3, cv.CV_8U);
      cv.dilate(edges, dilated, kernel);
      kernel.delete();

      cv.findContours(dilated, contours, hierarchy, cv.RETR_EXTERNAL, cv.CHAIN_APPROX_SIMPLE);

      // Find the largest contour
      let maxArea = 0;
      let maxIdx = -1;
      for (let i = 0; i < contours.size(); i++) {
        const area = cv.contourArea(contours.get(i));
        if (area > maxArea) {
          maxArea = area;
          maxIdx = i;
        }
      }

      const canvasArea = this.displayWidth * this.displayHeight;
      if (maxIdx >= 0 && maxArea > canvasArea * 0.05) {
        const contour = contours.get(maxIdx);
        const peri = cv.arcLength(contour, true);
        const approx = new cv.Mat();
        cv.approxPolyDP(contour, approx, 0.02 * peri, true);

        if (approx.rows === 4) {
          this.corners = [];
          for (let i = 0; i < 4; i++) {
            this.corners.push({
              x: approx.data32S[i * 2],
              y: approx.data32S[i * 2 + 1],
            });
          }
          this.orderCorners();
        } else {
          this.setDefaultCorners();
        }
        approx.delete();
      } else {
        this.setDefaultCorners();
      }
    } finally {
      src.delete();
      gray.delete();
      blurred.delete();
      edges.delete();
      dilated.delete();
      contours.delete();
      hierarchy.delete();
    }
  }

  private setDefaultCorners(): void {
    const margin = 0.1;
    const w = this.displayWidth;
    const h = this.displayHeight;
    this.corners = [
      { x: w * margin, y: h * margin },
      { x: w * (1 - margin), y: h * margin },
      { x: w * (1 - margin), y: h * (1 - margin) },
      { x: w * margin, y: h * (1 - margin) },
    ];
  }

  private orderCorners(): void {
    // Order: top-left, top-right, bottom-right, bottom-left
    const pts = [...this.corners];
    const sumXY = pts.map((p) => p.x + p.y);
    const diffXY = pts.map((p) => p.x - p.y);

    const tl = pts[sumXY.indexOf(Math.min(...sumXY))];
    const br = pts[sumXY.indexOf(Math.max(...sumXY))];
    const tr = pts[diffXY.indexOf(Math.max(...diffXY))];
    const bl = pts[diffXY.indexOf(Math.min(...diffXY))];

    this.corners = [tl, tr, br, bl];
  }

  redetect(): void {
    if (this.sourceImage && this.cv) {
      this.detectEdges(this.sourceImage);
    }
  }

  get polygonPoints(): string {
    return this.corners.map((c) => `${c.x},${c.y}`).join(' ');
  }

  // --- Drag handling (mouse) ---

  onCornerMouseDown(event: MouseEvent, index: number): void {
    event.preventDefault();
    this.draggingIndex = index;
    this.canvasRect = this.sourceCanvas.nativeElement.getBoundingClientRect();
  }

  onMouseMove(event: MouseEvent): void {
    if (this.draggingIndex < 0 || !this.canvasRect) return;
    event.preventDefault();
    this.updateCornerPosition(event.clientX, event.clientY);
  }

  onMouseUp(): void {
    this.draggingIndex = -1;
  }

  // --- Drag handling (touch) ---

  onCornerTouchStart(event: TouchEvent, index: number): void {
    event.preventDefault();
    this.draggingIndex = index;
    this.canvasRect = this.sourceCanvas.nativeElement.getBoundingClientRect();
  }

  onTouchMove(event: TouchEvent): void {
    if (this.draggingIndex < 0 || !this.canvasRect) return;
    event.preventDefault();
    const touch = event.touches[0];
    this.updateCornerPosition(touch.clientX, touch.clientY);
  }

  onTouchEnd(): void {
    this.draggingIndex = -1;
  }

  private updateCornerPosition(clientX: number, clientY: number): void {
    const rect = this.canvasRect!;
    const x = Math.max(0, Math.min(this.displayWidth, clientX - rect.left));
    const y = Math.max(0, Math.min(this.displayHeight, clientY - rect.top));
    this.corners[this.draggingIndex] = { x, y };
    // Trigger change detection by reassigning array
    this.corners = [...this.corners];
  }

  // --- Confirm: perspective transform ---

  confirm(): void {
    if (!this.cv || !this.sourceImage) return;

    const cv = this.cv;

    // Work with original image dimensions for quality
    const origCanvas = document.createElement('canvas');
    origCanvas.width = this.sourceImage.width;
    origCanvas.height = this.sourceImage.height;
    const origCtx = origCanvas.getContext('2d')!;
    origCtx.drawImage(this.sourceImage, 0, 0);

    const src = cv.imread(origCanvas);

    // Scale corners back to original image coordinates
    const srcPts = this.corners.map((c) => [c.x * this.scaleX, c.y * this.scaleY]);

    // Calculate output dimensions from the original-scale corners
    const widthTop = Math.hypot(srcPts[1][0] - srcPts[0][0], srcPts[1][1] - srcPts[0][1]);
    const widthBottom = Math.hypot(srcPts[2][0] - srcPts[3][0], srcPts[2][1] - srcPts[3][1]);
    const dstWidth = Math.round(Math.max(widthTop, widthBottom));

    const heightLeft = Math.hypot(srcPts[3][0] - srcPts[0][0], srcPts[3][1] - srcPts[0][1]);
    const heightRight = Math.hypot(srcPts[2][0] - srcPts[1][0], srcPts[2][1] - srcPts[1][1]);
    const dstHeight = Math.round(Math.max(heightLeft, heightRight));

    const srcMat = cv.matFromArray(4, 1, cv.CV_32FC2, srcPts.flat());
    const dstMat = cv.matFromArray(4, 1, cv.CV_32FC2, [
      0, 0,
      dstWidth, 0,
      dstWidth, dstHeight,
      0, dstHeight,
    ]);

    const M = cv.getPerspectiveTransform(srcMat, dstMat);
    const dst = new cv.Mat();
    cv.warpPerspective(src, dst, M, new cv.Size(dstWidth, dstHeight));

    // Show preview
    const preview = this.previewCanvas.nativeElement;
    cv.imshow(preview, dst);
    this.showPreview = true;

    // Export to blob
    preview.toBlob(
      (blob: Blob | null) => {
        if (blob) {
          this.scanned.emit(blob);
        }
      },
      'image/jpeg',
      0.92,
    );

    // Cleanup
    src.delete();
    srcMat.delete();
    dstMat.delete();
    M.delete();
    dst.delete();
  }

  cancel(): void {
    this.cancelled.emit();
  }
}
