package chipyard.fpga.u55c

//import protoacc.WithProtoAcc
import protoacc.WithProtoAccel
import org.chipsalliance.cde.config._

class WithU55CTweaks extends Config(
  new WithU55CAXIMemHarnessBinder ++
  //new chipyard.config.WithExtIn ++
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
  new WithProtoAccel()
)

class Protoaccu55cConfig extends Config (
  new chipyard.fpga.u55c.WithU55CTweaks ++
  new chipyard.config.WithNoDebug ++
  new chipyard.config.WithNoUART ++
  new protoacc.WithProtoAccel ++
  new freechips.rocketchip.rocket.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig 
)
