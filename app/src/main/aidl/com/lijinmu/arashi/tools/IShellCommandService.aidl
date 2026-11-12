package com.lijinmu.arashi.tools;

interface IShellCommandService {
    String[] execute(String command) = 0;
    void destroy() = 16777114;
}
