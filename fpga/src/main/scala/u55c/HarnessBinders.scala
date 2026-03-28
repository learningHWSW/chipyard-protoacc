package chipyard.fpga.u55c

import chisel3._
import chipyard.harness.{HarnessBinder}
import chipyard.iobinders._

class WithU55CAXIMemHarnessBinder extends HarnessBinder({
  case (th: U55CTestHarnessImpl, port: AXI4MemPort, chipId: Int) => {
    
    // 1. Grab the AXI parameters for both masters
    val memParams = port.io.bits.params
    val xdmaParams = th.outer.xdma_axi_io.params 

    // 2. Instantiate HBM with 2 ports and both sets of parameters
    val hbm = Module(new XilinxHBM(
      bundleParams = Seq(memParams, xdmaParams), 
      portNum = 2,
      is16GB = false,
      desiredName = s"hbm_chip_$chipId"
    ))

    hbm.io.HBM_REF_CLK_0 := th.outer.hbmClkSink.in.head._1.clock
    hbm.io.APB_0_PCLK := th.childClock
    hbm.io.APB_0_PRESET_N := !th.childReset.asBool
    hbm.io.APB_1_PCLK := th.childClock
    hbm.io.APB_1_PRESET_N := !th.childReset.asBool

    // 3. Wire Port 0 to the RISC-V SoC
    hbm.io.AXI_00_ACLK := th.childClock
    hbm.io.AXI_00_ARESET_N := !th.childReset.asBool
    hbm.io.AXI_00.drivenByStandardAXI4(port.io.bits, th.childClock, th.childReset.asBool)
    
    // 4. Wire Port 1 to the Host PC (XDMA)
    hbm.io.AXI_01_ACLK := th.childClock
    hbm.io.AXI_01_ARESET_N := !th.childReset.asBool
    hbm.io.AXI_01.drivenByStandardAXI4(th.outer.xdma_axi_io, th.childClock, th.childReset.asBool)
  }
})
