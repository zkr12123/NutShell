package sigmoid 

import chisel3._ 
import chisel3.util._

class SigmoidImpl extends Module {
    val io = IO(new Bundle {
        val in = Input(UInt(12.W))
        val out = Output(UInt(13.W))
    })

    // x: 3.8 => 4.8
    def constMult(x: UInt): UInt = { // x/ln2
        // res = x + (x >> 1) - (x >> 4) = x + (x >> 1) + ~(x >> 4) + 1

        // instantiate and connect CarrySaveAdder
        // Note: output is 12-bit
        
        val CSA = Module( new CarrySaveAdder(12, add1=true) ).io
        CSA.in.a := Cat(0.U, x) 
        CSA.in.b := Cat(0.U(2.W), x >> 1)
        CSA.in.c := ~Cat(0.U(5.W), x >> 4)
        //return output
        CSA.out(11, 0)
    }

    def comUnit(x: UInt): UInt = ~x + 1.U

    // x: 4
    def SGU(x: UInt): UInt = {
        val lambda = Wire(Vec(16, Bool()))

        lambda(15) := true.B 
        lambda(14) := x(3) || x(2) || x(1)
        lambda(13) := x(3) || x(2) || x(0)
        lambda(12) := x(3) || x(2)
        lambda(11) := x(3) || (x(1) && ~x(0)) || (~x(1) && x(0)) || (x(2) && x(1) && x(0))
        lambda(10) := x(3) || (x(2) && x(1)) || (x(1) && ~x(0))
        lambda(9)  := x(3) || (x(1) && x(0)) || (~x(2) && x(0))
        lambda(8)  := x(3) || (~x(2) && x(1) && x(0))
        lambda(7)  := ~x(2) && (x(1) || x(0)) || x(2) && ~x(1) && ~x(0)
        lambda(6)  := x(3) && x(1) || ~x(2) && x(1) && ~x(0) || x(2) && ~x(1) && ~x(0)
        lambda(5)  := x(3) && x(1) && x(0) || x(2) && ~x(1) || ~x(3) && ~x(1) && x(0)
        lambda(4)  := x(2) && ~x(1)
        lambda(3)  := ~x(3) && ~x(1) && x(0) || ~x(3) && x(1) && ~x(0) || ~x(3) && ~x(2) && x(1)
        lambda(2)  := x(2) && ~x(1) && x(0) || ~x(3) && x(1) && ~x(0) || ~x(3) && ~x(2) && x(1)
        lambda(1)  := ~x(3) && x(0) || ~x(3) && x(2) && x(1)
        lambda(0)  := x(2) && x(1)

        lambda.asUInt
    }

    // modified shift unit for scheme2
    // x: 3.8 (n=0) || 4.4 (n>0)
    // out: 0.16
    val MSU = Module( new ModifiedShiftUnit ).io
    val CSA = Module( new CarrySaveAdder(16, false) ).io
    
    // input unsigned
    val sign = io.in(11)
    val x = io.in(10, 0)
    val xu = Mux(sign, comUnit(x), x) // 3.8

    // CM out 
    val cmOut = constMult(xu)
    val phi = cmOut(7, 0) // 0.8
    val n = cmOut(11, 8) // 4.0

    val msu_in = Mux(
        n === 0.U, 
        Cat(0.U(1.W), xu), // 4.8
        Cat(0.U(4.W), phi) // 4.8
    )
    
    MSU.in.x := msu_in
    MSU.in.n := n 
    val w = MSU.out.w // 0.16
    val v = MSU.out.v // 0.16

    // SGU IO
    val lambda = SGU(n)

    // CSA IO
    CSA.in.a := w 
    CSA.in.b := v 
    CSA.in.c := lambda
    val s = CSA.out(16, 5) // 1.11

    // output
    val q = comUnit(Cat(0.U, s)) + "b0_1000_0000_0000".U // s1.11

    io.out := Mux(sign, q, s) // s1.11
}

class ModifiedShiftUnit extends Module {
    // n=0: x:3.8
    // else: x:4.8
    val io = IO(new Bundle {
        val in = new Bundle {
            val x = Input( UInt(12.W) )
            val n = Input( UInt(4.W) )
        }
        val out = new Bundle {
            val w = Output( UInt(16.W) )
            val v = Output( UInt(16.W) )
        }
    })

    val decM1: Array[(UInt, UInt)] = Array(
        "b0000".U  -> 2.U(4.W),
        "b0001".U  -> 3.U(4.W),
        "b0010".U  -> 4.U(4.W),
        "b0011".U  -> 5.U(4.W),
        "b0100".U  -> 5.U(4.W),
        "b0101".U  -> 6.U(4.W),
        "b0110".U  -> 7.U(4.W),
        "b0111".U  -> 8.U(4.W),
        "b1000".U  -> 9.U(4.W),
        "b1001".U  -> 10.U(4.W),
        "b1010".U  -> 11.U(4.W),
        "b1011".U  -> 12.U(4.W)
    )
    val decM2: Array[(UInt, UInt)] = Array(
        // "b0000".U  -> 5.U(3.W),
        "b0001".U  -> 7.U(3.W),
        "b0010".U  -> 5.U(3.W),
        "b0011".U  -> 6.U(3.W)
    )
    val defaultM1 = 0.U(4.W)
    val defaultM2 = 0.U(3.W)
    
    val m1 = MuxLookup(io.in.n, defaultM1, decM1)
    val m2 = MuxLookup(io.in.n, defaultM2, decM2)
    
    // 4.16
    val wvVec = Wire(UInt(20.W))
    wvVec := Cat(io.in.x, 0.U(8.W))
     
    io.out.w := (wvVec >> m1)(15, 0)
    io.out.v := Mux(m2 === 0.U, 0.U(16.W), (wvVec >> m2)(15, 0))
}

class CarrySaveAdder(width: Int, add1: Boolean) extends Module {
    val io = IO(new Bundle {
        val in = new Bundle {
            val a = Input( UInt(width.W) )
            val b = Input( UInt(width.W) )
            val c = Input( UInt(width.W) )
        }
        val out = Output( UInt( (width+2).W ) )
    })

    def FA(a: Bool, b: Bool, c: Bool): (Bool, Bool) = {
        val sum = a ^ b ^ c
        val cout = (a && b) || (b && c) || (a && c)
        (sum, cout)
    }

    def HA(a: Bool, b: Bool): (Bool, Bool) = {
        val sum = a ^ b 
        val cout = a && b 
        (sum, cout)
    }

    val one: Bool = add1.B
    // input UInt to Vec
    val vecA: Seq[Bool] = io.in.a.asBools
    val vecB: Seq[Bool] = io.in.b.asBools
    val vecC: Seq[Bool] = io.in.c.asBools

    val inter_res: Seq[(Bool, Bool)] = ( (vecA zip vecB) zip vecC ) map {
        case ( (a, b), c ) => FA(a, b, c)
    }

    // output Vec wire
    val vecSum: Vec[Bool] = Wire(Vec(width+2, Bool()))
    val vecCout: Vec[Bool] = Wire(Vec(width, Bool()))
    
    // Ripple-carry adders cascade
    if(add1) {
        // 1st HA
        HA(inter_res(0)._1, one) match {
            case (s, c) => {
                vecSum(0)  := s
                vecCout(0) := c 
            }
        }
        // remaining width-1 FA   
        for(i <- 1 until width) {
            FA(inter_res(i)._1, inter_res(i-1)._2, vecCout(i-1)) match {
                case (s, c) => {
                    vecSum(i)  := s
                    vecCout(i) := c
                }
            }
        }
        // last HA
        HA(inter_res(width-1)._2, vecCout(width-1)) match {
            case (s, c) => {
                vecSum(width)   := s 
                vecSum(width+1) := c
            }
        }
    } else {
        // 0th output
        vecSum(0) := inter_res(0)._1
        vecCout(0) := DontCare
        // 1st HA
        HA(inter_res(1)._1, inter_res(0)._2) match {
            case (s, c) => {
                vecSum(1)  := s 
                vecCout(1) := c
            }
        }
        // remaining width-2 FA
        for(i <- 2 until width) {
            FA(inter_res(i)._1, inter_res(i-1)._2, vecCout(i-1)) match {
                case (s, c) => {
                    vecSum(i)  := s 
                    vecCout(i) := c 
                }
            }
        }
        // last HA
        HA(inter_res(width-1)._2, vecCout(width-1)) match {
            case (s, c) => {
                vecSum(width)   := s 
                vecSum(width+1) := c 
            }
        }
    }

    // connect output
    io.out := vecSum.asUInt
}