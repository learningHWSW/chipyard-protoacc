package chipyard.fpga.u55c

import chisel3._
import chipyard.harness.HasHarnessInstantiators
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.lazymodule._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy.AddressSet
import freechips.rocketchip.prci._
//import protoacc.AXI4ProtoAcc
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.clocks.PLLFactoryKey
import sifive.fpgashells.shell._
import sifive.fpgashells.shell.xilinx._

class SysClockU55CPlacedOverlay
(
  val shell: U55CShellBasicOverlays,
  name: String,
  val designInput: ClockInputDesignInput,
  val shellInput: ClockInputShellInput,
  pPin: String, nPin: String
) extends LVDSClockInputXilinxPlacedOverlay(name, designInput, shellInput) {
  val node = shell { ClockSourceNode(freqMHz = 100, jitterPS = 50)(ValName(name)) }

  shell { InModuleBody {
    shell.xdc.addPackagePin(io.n, nPin)
    shell.xdc.addPackagePin(io.p, pPin)
    shell.xdc.addIOStandard(io.n, "LVDS")
    shell.xdc.addIOStandard(io.p, "LVDS")
  }}
}

class SysClockU55CShellPlacer
(
  shell: U55CShellBasicOverlays,
  val shellInput: ClockInputShellInput,
  pPin: String, nPin: String
)(implicit val valName: ValName) extends ClockInputShellPlacer[U55CShellBasicOverlays] {
  override def place(di: ClockInputDesignInput) = new SysClockU55CPlacedOverlay(shell, valName.value, di, shellInput, pPin, nPin)
}

abstract class U55CShellBasicOverlays()(implicit p: Parameters) extends UltraScaleShell {
  // PLL reset causes
  val pllReset = InModuleBody { Wire(Bool()) }
  // PCIe reset
  val pcie_rst_n = InModuleBody { Wire(Bool()) }

  val sys_clock = Overlay(ClockInputOverlayKey, new SysClockU55CShellPlacer(this, ClockInputShellInput(), "F24", "F23"))
  val hbm_clock = Overlay(ClockInputOverlayKey, new SysClockU55CShellPlacer(this, ClockInputShellInput(), "BK43", "BK44"))
  val xdma = Overlay(CustomXDMAOverlayKey, new U55CPCIeCustomXDMAShellPlacer(this, PCIeShellInput()))
}

class U55CFPGATestHarness(implicit p: Parameters) extends U55CShellBasicOverlays {
  def dp = designParameters
  // place all clocks in the shell
  require(dp(ClockInputOverlayKey).nonEmpty)
  val sysClkNode = dp(ClockInputOverlayKey).head.place(ClockInputDesignInput()).overlayOutput.node
  val hbmClkNode = dp(ClockInputOverlayKey).last.place(ClockInputDesignInput()).overlayOutput.node

  val hbmClkSink = freechips.rocketchip.prci.ClockSinkNode(Seq(freechips.rocketchip.prci.ClockSinkParameters()))
  hbmClkSink := hbmClkNode

  /*** Connect/Generate clocks ***/
  val harnessSysPLL = dp(PLLFactoryKey)()
  harnessSysPLL := sysClkNode

  val wrangler = LazyModule(new ResetWrangler)

  val dutFreqMHz = 70
  val dutFixedClockNode = FixedClockBroadcast()
  val dutClockGroup = ClockGroup()
  val dutClockNode = ClockSinkNode(freqMHz = dutFreqMHz, jitterPS = 230)
  dutFixedClockNode := wrangler.node := dutClockGroup := harnessSysPLL

  dutClockNode := dutFixedClockNode
  
  val placedXDMA = dp(CustomXDMAOverlayKey).head.place(CustomXDMADesignInput(wrangler.node, dutFixedClockNode))

  // 1. SINK FOR MAIN XDMA AXI PORT
  val xdma_axi_sink = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    slaves = Seq(AXI4SlaveParameters(
      address = Seq(AddressSet(0x00000000L, 0x3FFFFFFFFL)), // Match HBM 16GB address space
      resources = new freechips.rocketchip.resources.SimpleDevice("xdma_sink", Seq()).reg("xdma_sink"),
      regionType = freechips.rocketchip.diplomacy.RegionType.UNCACHED,
      executable = true,
      supportsRead = freechips.rocketchip.diplomacy.TransferSizes(1, 512),
      supportsWrite = freechips.rocketchip.diplomacy.TransferSizes(1, 512)
    )),
    beatBytes = 32 // Match XDMA busBytes width
  )))

  // Connect Master to Sink
  xdma_axi_sink := placedXDMA.overlayOutput.master
  
  // Expose hardware IO for HarnessBinders
  def xdma_axi_io = xdma_axi_sink.in.head._1


  // 2. SINK FOR XDMA AXI-LITE PORT (This fixes your error!)
  val xdma_axilite_sink = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    slaves = Seq(AXI4SlaveParameters(
      address = Seq(AddressSet(0x00000000L, 0xFFFFFFFFL)), // 32-bit address space
      resources = new freechips.rocketchip.resources.SimpleDevice("xdma_lite_sink", Seq()).reg("xdma_lite_sink"),
      regionType = freechips.rocketchip.diplomacy.RegionType.UNCACHED,
      executable = true,
      supportsRead = freechips.rocketchip.diplomacy.TransferSizes(1, 4),
      supportsWrite = freechips.rocketchip.diplomacy.TransferSizes(1, 4)
    )),
    beatBytes = 4 // 32-bit data width for AXI-Lite
  )))

  // Connect MasterLite to Sink
  xdma_axilite_sink := placedXDMA.overlayOutput.masterLite
  
  // Expose hardware IO
  def xdma_axilite_io = xdma_axilite_sink.in.head._1


  // Hardware Implementation phase begins here
  override lazy val module: LazyRawModuleImp = new U55CTestHarnessImpl(this)
}


class U55CTestHarnessImpl(val outer: U55CFPGATestHarness) extends LazyRawModuleImp(outer) with HasHarnessInstantiators {

  val prst_n = IO(Input(Bool())).suggestName("prst_n")
  outer.xdc.addPackagePin(prst_n, "BF41")
  outer.xdc.addIOStandard(prst_n, "LVCMOS18")
  outer.xdc.addPullup(prst_n)
  outer.sdc.addAsyncPath(Seq(prst_n))

  val pcie_rst_n_ibuff = Module(new IBUF)
  pcie_rst_n_ibuff.suggestName("pcie_rst_n_ibuff")
  pcie_rst_n_ibuff.io.I := prst_n
  outer.pcie_rst_n := pcie_rst_n_ibuff.io.O

  val sysclk: Clock = outer.sysClkNode.out.head._1.clock

  val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
  outer.sdc.addAsyncPath(Seq(powerOnReset))

  outer.pllReset := powerOnReset

  // reset setup
  val hReset = Wire(Reset())
  hReset := outer.dutClockNode.in.head._1.reset

  def referenceClockFreqMHz = outer.dutFreqMHz
  def referenceClock = outer.dutClockNode.in.head._1.clock
  def referenceReset = hReset
  val success = WireInit(false.B)

  childClock := referenceClock
  childReset := referenceReset

  instantiateChipTops()
}
