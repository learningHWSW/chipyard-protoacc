package protoacc

import chisel3._
import chisel3.util._
import org.chipsalliance.diplomacy.lazymodule._
import org.chipsalliance.cde.config._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy.{AddressSet, IdRange}
import freechips.rocketchip.regmapper.RegField

case class ProtoAccParams(
  nMemPorts: Int = 1,
  beatBytes: Int = 32,
)

case object ProtoAccKey extends Field[Option[ProtoAccParams]](None)

class WithProtoAcc(params: ProtoAccParams = ProtoAccParams()) extends Config((site, here, up) => {
  case ProtoAccKey => Some(params)
})

class AXI4ProtoAcc(implicit p: Parameters) extends LazyModule {
  val params = p(ProtoAccKey).getOrElse(ProtoAccParams())

  // AXI4 slave node for configuration and control registers
  val configNode = AXI4RegisterNode(
    address = AddressSet(0x8000, 0xff)
  )

  // AXI4 master node for memory access
  val memNode = AXI4MasterNode(Seq(AXI4MasterPortParameters(
    masters = Seq(AXI4MasterParameters(
      name = "protoacc",
      id = IdRange(0, 1 << 4)
    ))
  )))

  lazy val module = new LazyModuleImp(this) {
    // Simple status and control registers
    val status = RegInit(0.U(32.W))
    val ctrl   = RegInit(0.U(32.W))

    configNode.regmap(
      0x00 -> Seq(RegField.r(32, status)),
      0x04 -> Seq(RegField.w(32, ctrl)),
    )

    // Tie off memory master (placeholder: no DMA traffic)
    val (mem, _) = memNode.out.head
    mem.ar.valid  := false.B
    mem.ar.bits   := DontCare
    mem.aw.valid  := false.B
    mem.aw.bits   := DontCare
    mem.w.valid   := false.B
    mem.w.bits    := DontCare
    mem.r.ready   := false.B
    mem.b.ready   := false.B
  }
}
