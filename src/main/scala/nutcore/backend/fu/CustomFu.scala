package nutcore

import chisel3._ 
import chisel3.util._ 
import sigmoid.SigmoidImpl

object CustomFuOpType {
    def default = "b0000000".U 
}

//FunctionUnitIO: src1, src2, func(FuOptype)
//FuOptype = UInt(7.W)
class CustomFuIO extends FunctionUnitIO {
    val cfIn = Flipped(new CtrlFlowIO)
}

// class FuImplIO extends NutCoreBundle {
//     // Wrapper IO class for FU implementations
//     val in = Input(UInt(XLEN.W))
//     val out = Output(UInt(XLEN.W))
// }


class CustomFU extends NutCoreModule {
    // Wrapper class for single-cycle combinational logic
    val io = IO( new CustomFuIO )

    // input signals
    val valid = io.in.valid
    val src1 = io.in.bits.src1
    val src2 = io.in.bits.src2 // DontCare
    val func = io.in.bits.func //DontCare

    CustomFUType match {
        case "Sigmoid" => {
            val sigmoidHardware = Module(new SigmoidImpl).io
            sigmoidHardware.in := src1(11, 0)
            io.out.bits := sigmoidHardware.out
        }
        case "AddDec"  => {
            val addDecHardware = Module(new CustomDecAdder).io
            io <> addDecHardware
        }
        case _ => throw new Exception("CustomFU Not Implemented")
    }

    // output signals
    io.in.ready := io.out.ready
    io.out.valid := valid
    
}

// class SigmoidFu(hasFloatWrapper: Boolean=false) extends NutCoreModule {
//     val io = IO( new CustomFuIO )

//     val valid = io.in.valid
//     val src1 = io.in.bits.src1
    
//     val sigmoidHardware = Module(new SigmoidImpl).io

//     sigmoidHardware.in := src1(11, 0)

//     // out
//     io.in.ready := io.out.ready
//     io.out.valid := valid
//     io.out.bits := sigmoidHardware.out
// }



class CustomDecAdder extends NutCoreModule {
    val io = IO( new CustomFuIO )

    // input signals
    val valid = io.in.valid
    val src1 = io.in.bits.src1
    val src2 = io.in.bits.src2 // DontCare
    val func = io.in.bits.func //DontCare
    // val instr = io.cfIn.instr // will be used to decode funct3
    val funct3 = io.cfIn.instr(14,12)
    
    val decTable: Array[(UInt, UInt)] = Array(
        "b000".U  -> 1.U(XLEN.W),
        "b001".U  -> 10.U(XLEN.W),
        "b010".U  -> 100.U(XLEN.W),
        "b011".U  -> 1000.U(XLEN.W),
        "b100".U  -> 10000.U(XLEN.W),
        "b101".U  -> 100000.U(XLEN.W),
        "b110".U  -> 1000000.U(XLEN.W),
        "b111".U  -> 10000000.U(XLEN.W)
    )
    val default = 0.U(XLEN.W)
    val add = MuxLookup(funct3, default, decTable)

    val res = src1 +& add
    val outres = Mux(res(XLEN), Fill(XLEN, res(XLEN)), res(XLEN-1, 0))
    // capped to max uint if overflow


    // out
    io.in.ready := io.out.ready
    io.out.valid := valid
    io.out.bits := outres


}