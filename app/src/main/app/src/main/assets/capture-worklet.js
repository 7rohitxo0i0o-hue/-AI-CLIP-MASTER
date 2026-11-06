// AudioWorklet for the Shruti browser example: hands microphone samples to the page as mono float blocks of 4,096
// samples at the audio context's own rate (usually 44.1 or 48 kHz). The page converts them to 16 kHz. Nothing leaves
// the device.
class ShrutiCapture extends AudioWorkletProcessor {
  constructor() {
    super()
    this.block = new Float32Array(4096)
    this.n = 0
    this.running = true
    this.port.onmessage = (e) => {
      if (e.data === 'stop') {
        this.flush()
        this.running = false
      }
    }
  }

  flush() {
    if (!this.n) return
    const out = this.block.slice(0, this.n)
    this.port.postMessage(out, [out.buffer])
    this.n = 0
  }

  process(inputs) {
    const input = inputs[0]
    if (this.running && input && input.length) {
      const channels = input.length
      const first = input[0]
      for (let i = 0; i < first.length; i++) {
        let s = first[i]
        if (channels > 1) {
          for (let c = 1; c < channels; c++) s += input[c][i]
          s /= channels
        }
        this.block[this.n++] = s
        if (this.n === this.block.length) {
          const out = this.block
          this.port.postMessage(out, [out.buffer])
          this.block = new Float32Array(4096)
          this.n = 0
        }
      }
    }
    return this.running
  }
}

registerProcessor('shruti-capture', ShrutiCapture)
