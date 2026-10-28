from mcp.server import MCPServer
import subprocess
import asyncio

# Create the MCP server
mcp = MCPServer("Codein-Terminal-Agent")

@mcp.tool()
async def run_command(command: str) -> str:
    """Execute shell commands in Termux."""
    try:
        # Run command asynchronously
        process = await asyncio.create_subprocess_shell(
            command,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE
        )
        stdout, stderr = await process.communicate()
        
        return f"stdout: {stdout.decode()}\nstderr: {stderr.decode()}"
    except Exception as e:
        return f"Error executing command: {str(e)}"

if __name__ == "__main__":
    # Run using stdio transport
    asyncio.run(mcp.run())
