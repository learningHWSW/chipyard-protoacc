package chipyard.fpga.u55c

import protoacc.WithProtoAcc
import org.chipsalliance.cde.config._

class WithU55CTweaks extends Config(
  new WithU55CAXIMemHarnessBinder ++
  new chipyard.harness.WithTieOffL2FBusAXI ++
  // clocking
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new chipyard.harness.WithHarnessBinderClockFreqMHz(70) ++
  new chipyard.config.WithUniformBusFrequencies(70) ++
  new testchipip.serdes.WithNoSerialTL ++
  new testchipip.soc.WithNoScratchpads
)

// useful for xdma test
class EmptyU55CConfig extends Config (
  new WithU55CTweaks ++
  new chipyard.EmptyChipTopConfig ++
  new WithProtoAcc()
)

class Protoaccu55cConfig extends Config (
  new chipyard.fpga.u55c.WithU55CTweaks ++
  new chipyard.harness.WithBoardFPGAHarnessBinders ++
  new protoacc.WithProtoAcc ++
  new freechips.rocketchip.rocket.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig ++
  new chipyard.RocketConfig
)
