import re

with open("build/TopMain.v", "r") as f:
    line = f.readline()
    while(line):
        # read module name
        match_module = re.match(r"(module\s)(.+)(\()", line)

        if match_module:
            # write module file
            module_name = match_module.group(2)
            with open("build/module_sources/{}.v".format(module_name), "w") as wf:
                wf.write(match_module.group(0))
                
                # check next_line
                next_line = f.readline()
                match_end = re.match(r"endmodule", next_line)
                # code body
                while(match_end is None):
                    wf.write(next_line)
                    next_line = f.readline()
                    match_end = re.match(r"endmodule", next_line)
                # endmodule    
                wf.write(next_line)
                

        line = f.readline()